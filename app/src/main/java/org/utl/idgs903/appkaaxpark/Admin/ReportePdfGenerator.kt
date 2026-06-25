package org.utl.idgs903.appkaaxpark.Admin

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportePdfGenerator {

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGEN = 40f

    data class DatosReporte(
        val periodoLabel: String,
        val rangoLabel: String,
        val ocupacionPromedio: Int,
        val tiempoPromedio: String,
        val usuariosActivos: Int,
        val entradas: Int,
        val salidas: Int,
        val totalCajones: Int,
        val graficaBitmap: Bitmap?
    )

    fun generar(context: Context, datos: DatosReporte): Uri {
        val documento = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val pagina = documento.startPage(pageInfo)
        dibujarContenido(pagina.canvas, datos)
        documento.finishPage(pagina)

        val marcaTiempo = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val nombreArchivo = "KaaxPark_Reporte_$marcaTiempo.pdf"

        val uri = guardarDocumento(context, documento, nombreArchivo)
        documento.close()
        return uri
    }

    private fun dibujarContenido(canvas: Canvas, datos: DatosReporte) {
        canvas.drawColor(Color.WHITE)

        val paintTitulo = Paint().apply {
            color = Color.parseColor("#B8860B")
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintSubtitulo = Paint().apply {
            color = Color.parseColor("#555555")
            textSize = 12f
            isAntiAlias = true
        }
        val paintSeccion = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintEtiqueta = Paint().apply {
            color = Color.parseColor("#555555")
            textSize = 12f
            isAntiAlias = true
        }
        val paintValor = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintLinea = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            strokeWidth = 1f
        }

        var y = MARGEN + 10f
        canvas.drawText("K'áaxPark", MARGEN, y, paintTitulo)
        y += 20f
        canvas.drawText("Reporte de uso del estacionamiento", MARGEN, y, paintSubtitulo)
        y += 16f
        canvas.drawText("Periodo: ${datos.periodoLabel} (${datos.rangoLabel})", MARGEN, y, paintSubtitulo)
        y += 16f
        val fechaGeneracion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generado el $fechaGeneracion", MARGEN, y, paintSubtitulo)

        y += 20f
        canvas.drawLine(MARGEN, y, PAGE_WIDTH - MARGEN, y, paintLinea)
        y += 28f

        canvas.drawText("Resumen del periodo", MARGEN, y, paintSeccion)
        y += 22f

        val filas = listOf(
            "Ocupación promedio" to "${datos.ocupacionPromedio}%",
            "Tiempo promedio de estancia" to datos.tiempoPromedio,
            "Usuarios activos" to datos.usuariosActivos.toString(),
            "Entradas registradas" to datos.entradas.toString(),
            "Salidas registradas" to datos.salidas.toString(),
            "Total de cajones" to datos.totalCajones.toString()
        )

        filas.forEach { (etiqueta, valor) ->
            canvas.drawText(etiqueta, MARGEN, y, paintEtiqueta)
            canvas.drawText(valor, PAGE_WIDTH - MARGEN - paintValor.measureText(valor), y, paintValor)
            y += 20f
        }

        y += 12f
        canvas.drawLine(MARGEN, y, PAGE_WIDTH - MARGEN, y, paintLinea)
        y += 28f

        canvas.drawText("Tendencia de ocupación", MARGEN, y, paintSeccion)
        y += 16f

        val bitmap = datos.graficaBitmap
        if (bitmap != null && bitmap.width > 0) {
            val anchoDisponible = PAGE_WIDTH - (MARGEN * 2)
            val alturaEscalada = anchoDisponible * bitmap.height / bitmap.width
            val destino = Rect(
                MARGEN.toInt(),
                y.toInt(),
                (PAGE_WIDTH - MARGEN).toInt(),
                (y + alturaEscalada).toInt()
            )
            canvas.drawBitmap(bitmap, null, destino, null)
        } else {
            canvas.drawText("Sin datos suficientes para graficar.", MARGEN, y + 14f, paintEtiqueta)
        }
    }

    private fun guardarDocumento(context: Context, documento: PdfDocument, nombreArchivo: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val valores = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: throw IllegalStateException("No se pudo crear el archivo en Descargas.")
            resolver.openOutputStream(uri)?.use { salida ->
                documento.writeTo(salida)
            } ?: throw IllegalStateException("No se pudo abrir el archivo para escritura.")
            return uri
        }

        val carpetaDescargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!carpetaDescargas.exists()) {
            carpetaDescargas.mkdirs()
        }
        val archivo = File(carpetaDescargas, nombreArchivo)
        FileOutputStream(archivo).use { salida ->
            documento.writeTo(salida)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    }
}
