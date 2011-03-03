package simplewebsocket.river

import websocket.{WebSocketClient, WebSocket}

import org.elasticsearch.river.{AbstractRiverComponent, River, RiverName, RiverSettings}
import org.elasticsearch.common.inject.Inject
import org.elasticsearch.client.Client
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.cluster.block.ClusterBlockException
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.ExceptionsHelper
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.ActionListener
import org.elasticsearch.common.xcontent.support.XContentMapValues

class SimpleWebsocketRiver @Inject()(name: RiverName, settings: RiverSettings, client: Client)
  extends AbstractRiverComponent(name, settings)
  with River {

  logger.info("creating simple-websocket river")

  val indexName = "meetup"
  val typeName = "rsvp"
  val stream = new WebSocketHandler(new java.net.URI("ws://stream.meetup.com/2/rsvps"))

  var mapping: String = null

// TODO: check settings
  // Map<String, Object>
  if (settings.settings.containsKey("uri")) {
    val uri  = XContentMapValues.nodeStringValue(settings.settings.get("uri"), "None")
  }

  if (settings.settings.containsKey("mapping")) {
    val mappingSettings = settings.settings.get("mapping").asInstanceOf[java.util.Map[String, Object]]
    val builder = XContentFactory.jsonBuilder.map(mappingSettings)
    mapping = """{"%s":{"properties":%s}}""".format(typeName, builder.string)
  }


  override def close() = {
    logger.info("closing websocket simple-websocket")
    if (stream != null) {
      stream.close
    }
  }

  override def start(): Unit = {
    if (stream == null) {
      return
    }

    logger.info("starting websocket simple-websocket")

    try {
      if (mapping!=null) {
        client.admin.indices.prepareCreate(indexName).addMapping(typeName, mapping).execute.actionGet
      } else {
        client.admin.indices.prepareCreate(indexName).execute.actionGet
      }

    } catch {
      case e: Exception =>
        if (ExceptionsHelper.unwrapCause(e).isInstanceOf[IndexAlreadyExistsException]) {
          // that's fine
        } else if (ExceptionsHelper.unwrapCause(e).isInstanceOf[ClusterBlockException]) {
        } else {
          logger.warn("failed to create index %s, disabling simple-websocket river... %s".format(indexName, e))
          return
        }
    }

    stream.connect
  }
  class WebSocketHandler(uri: java.net.URI) {
    import WebSocket._
    logger.info("creating websocket handler")

    val wsc = new WebSocketClient(uri)({
      case OnOpen => logger.info("opening websocket client")
      case OnMessage(m) =>
        logger.debug("got message " + m.size)
        try {
          // TODO: id field
          val indexBuilder = client.prepareIndex(indexName, typeName)
          indexBuilder.setSource(m).execute(new ActionListener[IndexResponse]() {
            def onResponse(indexResponse: IndexResponse) {
              logger.debug("index executed: %s %s".format(indexResponse.getId, indexResponse.getIndex))
            }

            def onFailure(e: Throwable) {
              logger.error("failed to execute index")
            }
          })

        } catch {
          case e: Exception =>
            logger.error("failed to construct index request")
        }
    })

    // delegate to websocket client
    def connect = wsc.connect
    def close = wsc.close
  }

}


