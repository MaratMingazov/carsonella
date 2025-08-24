@file:OptIn(InternalResourceApi::class)

package carsonella.composeapp.generated.resources

import kotlin.OptIn
import kotlin.String
import kotlin.collections.MutableMap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem

private const val MD: String = "composeResources/carsonella.composeapp.generated.resources/"

internal val Res.drawable.France: DrawableResource by lazy {
      DrawableResource("drawable:France", setOf(
        ResourceItem(setOf(), "${MD}drawable/France.png", -1, -1),
      ))
    }

internal val Res.drawable.Japan: DrawableResource by lazy {
      DrawableResource("drawable:Japan", setOf(
        ResourceItem(setOf(), "${MD}drawable/Japan.png", -1, -1),
      ))
    }

internal val Res.drawable.compose_multiplatform: DrawableResource by lazy {
      DrawableResource("drawable:compose_multiplatform", setOf(
        ResourceItem(setOf(), "${MD}drawable/compose-multiplatform.xml", -1, -1),
      ))
    }

@InternalResourceApi
internal fun _collectCommonMainDrawable0Resources(map: MutableMap<String, DrawableResource>) {
  map.put("France", Res.drawable.France)
  map.put("Japan", Res.drawable.Japan)
  map.put("compose_multiplatform", Res.drawable.compose_multiplatform)
}
