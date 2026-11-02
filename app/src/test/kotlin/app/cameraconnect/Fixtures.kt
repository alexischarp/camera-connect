package app.cameraconnect

/** Loads a test fixture from src/test/resources/fixtures. */
object Fixtures {
    fun read(name: String): String =
        checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture $name"
        }.use { it.readBytes().toString(Charsets.UTF_8) }
}
