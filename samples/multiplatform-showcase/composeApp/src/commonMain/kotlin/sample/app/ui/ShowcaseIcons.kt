package sample.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight inline Material vector icons used by the showcase app so it does not
 * pull in `material-icons-extended` (~2,500 icons, adding ~10 min to Kotlin/Native linking).
 */
internal object ShowcaseIcons {

  val Menu: ImageVector by lazy {
    ImageVector.Builder(
      name = "ShowcaseIcons.Menu",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter
      ) {
        moveTo(3f, 18f)
        horizontalLineToRelative(18f)
        verticalLineToRelative(-2f)
        horizontalLineTo(3f)
        verticalLineToRelative(2f)
        close()
        moveTo(3f, 13f)
        horizontalLineToRelative(18f)
        verticalLineToRelative(-2f)
        horizontalLineTo(3f)
        verticalLineToRelative(2f)
        close()
        moveTo(3f, 6f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(18f)
        verticalLineTo(6f)
        horizontalLineTo(3f)
        close()
      }
    }.build()
  }

  val Settings: ImageVector by lazy {
    ImageVector.Builder(
      name = "ShowcaseIcons.Settings",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter
      ) {
        moveTo(19.14f, 12.94f)
        curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
        curveToRelative(0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
        lineToRelative(2.03f, -1.58f)
        curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
        lineToRelative(-1.92f, -3.32f)
        curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
        lineToRelative(-2.39f, 0.96f)
        curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
        lineTo(14.4f, 2.81f)
        curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
        horizontalLineToRelative(-3.84f)
        curveToRelative(-0.24f, 0f, -0.43f, 0.17f, -0.47f, 0.41f)
        lineTo(9.25f, 5.35f)
        curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
        lineTo(5.24f, 5.33f)
        curveToRelative(-0.22f, -0.08f, -0.47f, 0f, -0.59f, 0.22f)
        lineTo(2.74f, 8.87f)
        curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
        lineToRelative(2.03f, 1.58f)
        curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
        reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
        lineToRelative(-2.03f, 1.58f)
        curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
        lineToRelative(1.92f, 3.32f)
        curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
        lineToRelative(2.39f, -0.96f)
        curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
        lineToRelative(0.36f, 2.54f)
        curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
        horizontalLineToRelative(3.84f)
        curveToRelative(0.24f, 0f, 0.44f, -0.17f, 0.47f, -0.41f)
        lineToRelative(0.36f, -2.54f)
        curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
        lineToRelative(2.39f, 0.96f)
        curveToRelative(0.22f, 0.08f, 0.47f, 0f, 0.59f, -0.22f)
        lineToRelative(1.92f, -3.32f)
        curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
        lineTo(19.14f, 12.94f)
        close()
        moveTo(12f, 15.6f)
        curveToRelative(-1.98f, 0f, -3.6f, -1.62f, -3.6f, -3.6f)
        reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
        reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
        reflectiveCurveTo(13.98f, 15.6f, 12f, 15.6f)
        close()
      }
    }.build()
  }

  val Visibility: ImageVector by lazy {
    ImageVector.Builder(
      name = "ShowcaseIcons.Visibility",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter
      ) {
        moveTo(12f, 4.5f)
        curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
        curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
        reflectiveCurveToRelative(9.27f, -3.11f, 11f, -7.5f)
        curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
        close()
        moveTo(12f, 17f)
        curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
        reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
        reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
        reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
        close()
        moveTo(12f, 9f)
        curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
        reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
        reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
        reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
        close()
      }
    }.build()
  }

  val VisibilityOff: ImageVector by lazy {
    ImageVector.Builder(
      name = "ShowcaseIcons.VisibilityOff",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter
      ) {
        moveTo(12f, 7f)
        curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
        curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
        lineToRelative(2.92f, 2.92f)
        curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
        curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
        curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
        lineToRelative(2.16f, 2.16f)
        curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
        close()
        moveTo(2f, 4.27f)
        lineToRelative(2.28f, 2.28f)
        lineToRelative(0.46f, 0.46f)
        curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
        curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
        curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
        lineToRelative(0.42f, 0.42f)
        lineTo(19.73f, 22f)
        lineTo(21f, 20.73f)
        lineTo(3.27f, 3f)
        lineTo(2f, 4.27f)
        close()
        moveTo(7.53f, 9.8f)
        lineToRelative(1.55f, 1.55f)
        curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
        curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
        curveToRelative(0.22f, 0f, 0.44f, -0.03f, 0.65f, -0.08f)
        lineToRelative(1.55f, 1.55f)
        curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
        curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
        curveToRelative(0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
        close()
        moveTo(11.84f, 9.02f)
        lineToRelative(3.15f, 3.15f)
        lineToRelative(0.02f, -0.16f)
        curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
        lineToRelative(-0.17f, 0.01f)
        close()
      }
    }.build()
  }
}
