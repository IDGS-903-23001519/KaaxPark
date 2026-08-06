package org.utl.idgs903.appkaaxpark.Admin

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReportePdfGenerator {

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGEN = 36f

    data class ItemResumen(
        val label: String,
        val valor: String,
        val seccion: String = "Resumen General"
    )

    data class PuntoGrafica(
        val etiqueta: String,
        val valor: Float,
        val valorFormateado: String
    )

    data class DatosReporte(
        val tituloReporte: String,
        val moduloNombre: String,
        val periodoLabel: String,
        val rangoLabel: String,
        val itemsResumen: List<ItemResumen>,
        val tituloGrafica: String = "TENDENCIA Y ANÁLISIS",
        val leyendaEjeY: String = "Porcentaje / Monto",
        val leyendaEjeX: String = "Periodo de Tiempo",
        val puntosGrafica: List<PuntoGrafica> = emptyList(),
        val graficaBitmap: Bitmap? = null
    )

    fun generar(context: Context, datos: DatosReporte): Uri {
        val documento = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val pagina = documento.startPage(pageInfo)
        dibujarContenido(pagina.canvas, datos)
        documento.finishPage(pagina)

        val marcaTiempo = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val nombreLimpio = datos.moduloNombre.replace(Regex("[^a-zA-Z0-9]"), "_")
        val nombreArchivo = "KaaxPark_Reporte_${nombreLimpio}_$marcaTiempo.pdf"

        val uri = guardarDocumento(context, documento, nombreArchivo)
        documento.close()
        return uri
    }

    private fun dibujarContenido(canvas: Canvas, datos: DatosReporte) {
        canvas.drawColor(Color.WHITE)

        val colorDorado = Color.parseColor("#C9A227")
        val colorBordeGris = Color.parseColor("#E0E0E0")
        val colorTextoOscuro = Color.parseColor("#111111")
        val colorTextoGris = Color.parseColor("#666666")

        // 1. Barra superior dorada
        val paintTopBar = Paint().apply {
            color = colorDorado
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, paintTopBar)

        var y = MARGEN + 16f

        // 2. Encabezado Institucional (Brand & Meta)
        val paintBrand = Paint().apply {
            color = colorDorado
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintSubBrand = Paint().apply {
            color = colorTextoGris
            textSize = 9f
            isAntiAlias = true
        }
        val paintMetaTitle = Paint().apply {
            color = colorTextoOscuro
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val paintMetaSub = Paint().apply {
            color = colorTextoGris
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("K'ÁAXPARK", MARGEN, y, paintBrand)
        canvas.drawText(datos.tituloReporte.uppercase(), PAGE_WIDTH - MARGEN, y, paintMetaTitle)
        
        y += 14f
        canvas.drawText("Sistema de Gestión e Inteligencia de Estacionamientos", MARGEN, y, paintSubBrand)
        
        val fechaEmision = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generado el: $fechaEmision", PAGE_WIDTH - MARGEN, y, paintMetaSub)

        // Línea divisoria de encabezado
        y += 12f
        val paintHeaderLine = Paint().apply {
            color = colorDorado
            strokeWidth = 2f
        }
        canvas.drawLine(MARGEN, y, PAGE_WIDTH - MARGEN, y, paintHeaderLine)

        // 3. Tarjetas / Cuadro de Resumen de Metadatos KPI
        y += 16f
        val kpiBoxHeight = 44f
        val kpiRect = RectF(MARGEN, y, PAGE_WIDTH - MARGEN, y + kpiBoxHeight)
        val paintKpiBg = Paint().apply {
            color = Color.parseColor("#FAFAFA")
            style = Paint.Style.FILL
        }
        val paintKpiBorder = Paint().apply {
            color = colorBordeGris
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(kpiRect, 6f, 6f, paintKpiBg)
        canvas.drawRoundRect(kpiRect, 6f, 6f, paintKpiBorder)

        val colWidth = (PAGE_WIDTH - (MARGEN * 2)) / 3f
        
        // Columna 1: Módulo
        val paintKpiLabel = Paint().apply {
            color = colorTextoGris
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintKpiValue = Paint().apply {
            color = colorTextoOscuro
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        var xCol = MARGEN + 12f
        var yKpi = y + 16f
        canvas.drawText("MÓDULO DE REPORTE", xCol, yKpi, paintKpiLabel)
        canvas.drawText(datos.moduloNombre.uppercase(), xCol, yKpi + 14f, paintKpiValue)

        // Columna 2: Rango
        xCol += colWidth
        canvas.drawText("PERIODO / RANGO DE FECHAS", xCol, yKpi, paintKpiLabel)
        canvas.drawText("${datos.periodoLabel} (${datos.rangoLabel})", xCol, yKpi + 14f, paintKpiValue)

        // Columna 3: Estado
        xCol += colWidth
        canvas.drawText("ESTADO DEL DOCUMENTO", xCol, yKpi, paintKpiLabel)
        val paintBadge = Paint().apply {
            color = colorDorado
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("OFICIAL / VERIFICADO", xCol, yKpi + 14f, paintBadge)

        y += kpiBoxHeight + 20f

        // 4. Secciones y Tablas de Resumen
        val grupos = datos.itemsResumen.groupBy { it.seccion }

        val paintSecTitle = Paint().apply {
            color = colorDorado
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintTableThBg = Paint().apply {
            color = colorDorado
            style = Paint.Style.FILL
        }
        val paintThText = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintTdLabel = Paint().apply {
            color = colorTextoOscuro
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintTdValue = Paint().apply {
            color = colorTextoOscuro
            textSize = 9.5f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val paintRowBgEven = Paint().apply {
            color = Color.parseColor("#FCFCFC")
            style = Paint.Style.FILL
        }
        val paintRowLine = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 1f
        }

        grupos.forEach { (seccion, items) ->
            // Título de Sección
            canvas.drawText(seccion.uppercase(), MARGEN, y, paintSecTitle)
            y += 6f
            canvas.drawLine(MARGEN, y, PAGE_WIDTH - MARGEN, y, paintRowLine)
            y += 10f

            // Encabezado de Tabla (TH)
            val tableWidth = PAGE_WIDTH - (MARGEN * 2)
            val thHeight = 22f
            canvas.drawRect(MARGEN, y, MARGEN + tableWidth, y + thHeight, paintTableThBg)
            canvas.drawText("MÉTRICA / INDICADOR", MARGEN + 10f, y + 14f, paintThText)
            canvas.drawText("VALOR CALCULADO", MARGEN + (tableWidth * 0.55f), y + 14f, paintThText)
            y += thHeight

            // Filas de Tabla (TD)
            items.forEachIndexed { index, item ->
                val trHeight = 20f
                if (index % 2 == 1) {
                    canvas.drawRect(MARGEN, y, MARGEN + tableWidth, y + trHeight, paintRowBgEven)
                }

                canvas.drawText(item.label, MARGEN + 10f, y + 14f, paintTdLabel)
                canvas.drawText(item.valor, MARGEN + (tableWidth * 0.55f), y + 14f, paintTdValue)

                y += trHeight
                canvas.drawLine(MARGEN, y, MARGEN + tableWidth, y, paintRowLine)
            }

            y += 16f
        }

        // 5. Gráfica Vectorial Profesional de Alta Definición
        val puntos = datos.puntosGrafica
        if (puntos.isNotEmpty() && y < PAGE_HEIGHT - 170f) {
            val tituloGraficaSec = datos.tituloGrafica.uppercase()
            canvas.drawText(tituloGraficaSec, MARGEN, y, paintSecTitle)
            y += 6f
            canvas.drawLine(MARGEN, y, PAGE_WIDTH - MARGEN, y, paintRowLine)
            y += 12f

            // Recuadro Contenedor de Gráfica
            val chartBoxWidth = PAGE_WIDTH - (MARGEN * 2)
            val chartBoxHeight = 145f
            val chartRect = RectF(MARGEN, y, MARGEN + chartBoxWidth, y + chartBoxHeight)
            
            val paintChartBg = Paint().apply {
                color = Color.parseColor("#FAFAFA")
                style = Paint.Style.FILL
            }
            val paintChartBorder = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(chartRect, 6f, 6f, paintChartBg)
            canvas.drawRoundRect(chartRect, 6f, 6f, paintChartBorder)

            // Explicación Clara de los Ejes Y (Altura) y X (Ancho)
            val paintAxisLegendY = Paint().apply {
                color = Color.parseColor("#666666")
                textSize = 8f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("▲ Altura (Eje Y): ${datos.leyendaEjeY}", MARGEN + 10f, y + 14f, paintAxisLegendY)

            val paintAxisLegendX = Paint().apply {
                color = Color.parseColor("#666666")
                textSize = 8f
                isFakeBoldText = true
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            canvas.drawText("► Ancho (Eje X): ${datos.leyendaEjeX}", MARGEN + chartBoxWidth - 10f, y + 14f, paintAxisLegendX)

            // Área de ploteo interna
            val plotLeft = MARGEN + 42f
            val plotRight = MARGEN + chartBoxWidth - 22f
            val plotTop = y + 32f
            val plotBottom = y + chartBoxHeight - 22f
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop

            // Líneas de cuadrícula horizontal y valores del eje Y
            val maxVal = maxOf(1f, puntos.maxOf { it.valor })
            val gridCount = 3
            val paintGridLine = Paint().apply {
                color = Color.parseColor("#EEEEEE")
                strokeWidth = 1f
            }
            val paintYAxisText = Paint().apply {
                color = Color.parseColor("#777777")
                textSize = 7.5f
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }

            for (i in 0..gridCount) {
                val ratio = i.toFloat() / gridCount
                val yLine = plotBottom - (ratio * plotHeight)
                val valStep = (maxVal * ratio).toInt()
                canvas.drawLine(plotLeft, yLine, plotRight, yLine, paintGridLine)
                canvas.drawText("$valStep", plotLeft - 5f, yLine + 3f, paintYAxisText)
            }

            // Calcular coordenadas de cada punto
            val coords = mutableListOf<Pair<Float, Float>>()
            val stepX = if (puntos.size > 1) plotWidth / (puntos.size - 1) else plotWidth / 2f

            puntos.forEachIndexed { i, pt ->
                val px = if (puntos.size > 1) plotLeft + (i * stepX) else plotLeft + (plotWidth / 2f)
                val py = plotBottom - (pt.valor / maxVal * plotHeight)
                coords.add(px to py)
            }

            // Relleno suave bajo la curva
            if (coords.isNotEmpty()) {
                val fillPath = Path().apply {
                    moveTo(coords[0].first, plotBottom)
                    coords.forEach { lineTo(it.first, it.second) }
                    lineTo(coords.last().first, plotBottom)
                    close()
                }
                val paintFill = Paint().apply {
                    color = colorDorado
                    alpha = 40
                    style = Paint.Style.FILL
                }
                canvas.drawPath(fillPath, paintFill)

                // Trazo de la curva vectorial
                val linePath = Path().apply {
                    moveTo(coords[0].first, coords[0].second)
                    for (i in 1 until coords.size) {
                        lineTo(coords[i].first, coords[i].second)
                    }
                }
                val paintCurve = Paint().apply {
                    color = colorDorado
                    strokeWidth = 2.2f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawPath(linePath, paintCurve)
            }

            // Dibujar Puntos, Valores exactos y Etiquetas del Eje X
            val paintPtOuter = Paint().apply { color = colorDorado; style = Paint.Style.FILL; isAntiAlias = true }
            val paintPtInner = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
            val paintPtValue = Paint().apply {
                color = colorTextoOscuro
                textSize = 8f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val paintXLabel = Paint().apply {
                color = Color.parseColor("#666666")
                textSize = 7.5f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            puntos.forEachIndexed { i, pt ->
                val (px, py) = coords[i]
                // Punto Vectorial
                canvas.drawCircle(px, py, 3.8f, paintPtOuter)
                canvas.drawCircle(px, py, 2f, paintPtInner)

                // Valor exacto resaltado sobre el punto
                canvas.drawText(pt.valorFormateado, px, py - 5f, paintPtValue)

                // Etiqueta de Fecha/Categoría del Eje X abajo
                canvas.drawText(pt.etiqueta, px, plotBottom + 12f, paintXLabel)
            }

            y += chartBoxHeight + 16f
        }

        // 6. Pie de Página Profesional
        val yFooter = PAGE_HEIGHT - MARGEN
        val paintFooterLine = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            strokeWidth = 1f
        }
        canvas.drawLine(MARGEN, yFooter - 12f, PAGE_WIDTH - MARGEN, yFooter - 12f, paintFooterLine)

        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
        val paintFooterText = Paint().apply {
            color = Color.parseColor("#A0A0A0")
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "K'áaxPark Parking System © $anioActual — Reporte Oficial de Inteligencia Operativa",
            PAGE_WIDTH / 2f,
            yFooter,
            paintFooterText
        )
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
