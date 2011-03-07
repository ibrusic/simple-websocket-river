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
import org.elasticsearch.common.xcontent.json.JsonXContent

case class SimpleWebsocketRiverConfiguration(indexName: String, typeName: String, uri: java.net.URI,
                                             mapping: String, idField: String, bulkSize: Int, dropThreshold: Int)

class SimpleWebsocketRiver @Inject()(name: RiverName, settings: RiverSettings, client: Client)
  extends AbstractRiverComponent(name, settings)
  with River {

  logger.info("creating simple-websocket river")

  var stream: WebSocketHandler = null
  val config = createConfiguration(settings)
  if (config!=null)
    stream = new WebSocketHandler(config.uri)

  override def close() {
    logger.info("closing websocket simple-websocket")
    if (stream != null) {
      stream.close
    }
  }

  override def start() {
    if (stream == null) {
      return
    }

    logger.info("starting websocket simple-websocket")

    try {
      if (config.mapping!=null) {
        client.admin.indices.prepareCreate(config.indexName).addMapping(config.typeName, config.mapping).execute.actionGet
      } else {
        client.admin.indices.prepareCreate(config.indexName).execute.actionGet
      }

    } catch {
      case e: Exception =>
        if (ExceptionsHelper.unwrapCause(e).isInstanceOf[IndexAlreadyExistsException]) {
          // that's fine
        } else if (ExceptionsHelper.unwrapCause(e).isInstanceOf[ClusterBlockException]) {
        } else {
          logger.warn("failed to create index %s, disabling simple-websocket river... %s".format(config.indexName, e))
          return
        }
    }

    stream.connect
  }

  private def createConfiguration(settings: RiverSettings): SimpleWebsocketRiverConfiguration = {
    var indexName = riverName.name
    var typeName = "websocket"

    var mapping: String = null
    var idField: String = null
    var bulkSize = 100
    var dropThreshold = 10

    val uri = XContentMapValues.nodeStringValue(settings.settings.get("uri"), null)
    if (uri==null) {
      // the websocket uri is the only required parameter
      logger.error("no websocket URI specified, disabling river...")
      return null
    } else {
      idField = XContentMapValues.nodeStringValue(settings.settings.get("id_field"), null)

      if (settings.settings.containsKey("mapping")) {
        val mappingSettings = settings.settings.get("mapping").asInstanceOf[java.util.Map[String, Object]]
        val builder = XContentFactory.jsonBuilder.map(mappingSettings)
        mapping = """{"%s":{"properties":%s}}""".format(typeName, builder.string)
      }

      if (settings.settings().containsKey("index")) {
        val indexSettings = settings.settings.get("index").asInstanceOf[java.util.Map[String, Object]]
        indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name)
        typeName = XContentMapValues.nodeStringValue(indexSettings.get("type"), "websocket")
        bulkSize = XContentMapValues.nodeIntegerValue(settings.settings().get("bulk_size"), 100)
        dropThreshold = XContentMapValues.nodeIntegerValue(settings.settings().get("drop_threshold"), 10)
      } else {
        indexName = riverName.name()
        typeName = "status"
        bulkSize = 100
        dropThreshold = 10
      }


    }
    SimpleWebsocketRiverConfiguration(indexName, typeName, new java.net.URI(uri), mapping, idField, bulkSize, dropThreshold)
  }

  class WebSocketHandler(uri: java.net.URI) {
    import WebSocket._
    logger.info("creating websocket handler")

    val wsc = new WebSocketClient(uri)({
      case OnOpen => logger.info("opening websocket client")
      case OnMessage(msg) =>
        logger.debug("got message " + msg.size)
        val parser = JsonXContent.jsonXContent.createParser(msg)
        val msgMap = parser.map

        try {
          /*
          // TODO: iterate through a list of include or exclude fields
          var source = Map[String, String]()
          val includeFields = List[String]()
          includeFields.foreach { field =>
            val value = msgMap.get(field)
            //source = source + (field -> value)
          }
          */

          var indexBuilder = client.prepareIndex(config.indexName, config.typeName).setSource(msg)

          // set the id if a field has been defined and the value can be found in the JSON
          if (config.idField!= null && msgMap.get(config.idField) != null) {
            indexBuilder = indexBuilder.setId(msgMap.get(config.idField).toString)
          }

          indexBuilder.execute(new ActionListener[IndexResponse]() {
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


