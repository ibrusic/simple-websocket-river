package simplewebsocket.river

import org.elasticsearch.common.inject.AbstractModule
import org.elasticsearch.river.River

class SimpleWebsocketRiverModule extends AbstractModule {

    override def configure = bind(classOf[River]).to(classOf[SimpleWebsocketRiver]).asEagerSingleton()
}
