package app.cameraconnect.discovery.upnp

import app.cameraconnect.discovery.upnp.XmlDom.childElements
import app.cameraconnect.discovery.upnp.XmlDom.childText
import app.cameraconnect.discovery.upnp.XmlDom.firstChild
import app.cameraconnect.discovery.upnp.XmlDom.localNameOrTag

data class ScpdArgument(val name: String, val direction: String?, val relatedStateVariable: String?)

data class ScpdAction(val name: String, val arguments: List<ScpdArgument>) {
    fun signature(): String {
        val inputs = arguments.filter { it.direction.equals("in", ignoreCase = true) }.joinToString { it.name }
        val outputs = arguments.filter { it.direction.equals("out", ignoreCase = true) }.joinToString { it.name }
        return "$name(in: $inputs) -> ($outputs)"
    }
}

data class ScpdStateVariable(val name: String, val dataType: String?, val allowedValues: List<String>)

/** Service Control Protocol Description: the list of actions a UPnP service accepts. */
data class Scpd(val actions: List<ScpdAction>, val stateVariables: List<ScpdStateVariable>) {
    fun hasAction(name: String): Boolean = actions.any { it.name.equals(name, ignoreCase = true) }
}

object ScpdParser {

    fun parse(xml: String): Scpd {
        val root = XmlDom.parse(xml).documentElement ?: throw UpnpParseException("Empty SCPD")
        val actions = root.firstChild("actionList")?.childElements().orEmpty()
            .filter { it.localNameOrTag == "action" }
            .mapNotNull { action ->
                val name = action.childText("name") ?: return@mapNotNull null
                val arguments = action.firstChild("argumentList")?.childElements().orEmpty()
                    .filter { it.localNameOrTag == "argument" }
                    .mapNotNull { argument ->
                        val argumentName = argument.childText("name") ?: return@mapNotNull null
                        ScpdArgument(
                            name = argumentName,
                            direction = argument.childText("direction"),
                            relatedStateVariable = argument.childText("relatedStateVariable"),
                        )
                    }
                ScpdAction(name, arguments)
            }
        val variables = root.firstChild("serviceStateTable")?.childElements().orEmpty()
            .filter { it.localNameOrTag == "stateVariable" }
            .mapNotNull { variable ->
                val name = variable.childText("name") ?: return@mapNotNull null
                val allowed = variable.firstChild("allowedValueList")?.childElements().orEmpty()
                    .filter { it.localNameOrTag == "allowedValue" }
                    .map { it.textContent.trim() }
                ScpdStateVariable(name, variable.childText("dataType"), allowed)
            }
        return Scpd(actions, variables)
    }
}
