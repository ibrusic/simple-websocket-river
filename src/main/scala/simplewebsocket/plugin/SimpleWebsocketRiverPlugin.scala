package simplewebsocket.plugin

import simplewebsocket.river.SimpleWebsocketRiverModule
import org.elasticsearch.plugins.AbstractPlugin
import org.elasticsearch.common.inject.{Inject, Module}
import org.elasticsearch.river.RiversModule

class SimpleWebsocketRiverPlugin @Inject() extends AbstractPlugin {

  override def name = "river-simple-websocket"

  override def description = "River Simple Websocket Plugin"

  override def processModule(module: Module) = {
    if (module.isInstanceOf[RiversModule]) {
      module.asInstanceOf[RiversModule].registerRiver("simple-websocket", classOf[SimpleWebsocketRiverModule])
    }
  }
}