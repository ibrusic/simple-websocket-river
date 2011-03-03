import sbt._

class RiverProject(info: ProjectInfo) extends DefaultProject(info) {

  val sonatypeReleases = "Sonatype Maven2 Snapshots Repository" at "http://oss.sonatype.org/content/repositories/releases/"
  val sonatypeSnapshots = "Sonatype Maven2 Snapshots Repository" at "http://oss.sonatype.org/content/repositories/snapshots/"

  /*
  val mavenLocal = "Local Maven Repository" at "file://"+Path.userHome+"/.m2/repository"
  val javaSnapshots = "Java Maven2 Snapshots Repository" at "http://download.java.net/maven/2/"
  override def ivyRepositories = Seq(Resolver.defaultLocal(None)) ++ repositories
  val jmxri = "com.sun.jmx" % "jmxri" % "1.2.1" from "file:///Users/ivan/.m2/repository/com/sun/jmx/jmxri/1.2.1/jmxri-1.2.1.jar"
   */

  val pluginName = "simple-websocket"
  val elasticsearchVersion = "0.16.0-SNAPSHOT"
  val elasticsearchJarPluginName = "elasticsearch-%s-%s.zip".format(pluginName, elasticsearchVersion)

  val elasticsearch = "org.elasticsearch" % "elasticsearch" % elasticsearchVersion

  lazy val `package-elasticsearch` = packageElasticsearch
  lazy val packageElasticsearch =
    zipTask(((outputPath ##) / defaultJarName) +++ mainDependencies.scalaJars, outputPath / pluginName, elasticsearchJarPluginName)
      .dependsOn(`package`)
      .describedAs("Zips up the simplewebsocket.plugin using the required elasticsearch filename format.")
}