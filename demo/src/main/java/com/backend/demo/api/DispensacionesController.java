package com.backend.demo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("api/rest/documentos")
public class DispensacionesController {

    private static final Logger logger = LoggerFactory.getLogger(DispensacionesController.class);

    @Value("${documentos.ruta.base}")
    private String rutaBase;

    // Crea la carpeta si no existe al arrancar
    @PostConstruct
    public void crearCarpetaSiNoExiste() {
        File carpeta = new File(rutaBase);
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }
    }

    @GetMapping
    public ResponseEntity<List<String>> obtenerDocumentos(
            @RequestParam String pacienteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletRequest request) {

        Path directorio = Paths.get(rutaBase);

        if (!Files.exists(directorio) || !Files.isDirectory(directorio)) {
            logger.info("Directorio no encontrado: " + rutaBase);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList("Directorio no encontrado"));
        }

        List<String> enlaces = new ArrayList<>();
        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .build()
                .toUriString();

        try (Stream<Path> archivos = Files.list(directorio)) {

            archivos
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .forEach(path -> {

                        String nombre = path.getFileName().toString();

                        logger.info("Nombre fichero: " + nombre);

                        if (nombre.startsWith(pacienteId + "_")) {
                            String[] partes = nombre.replace(".pdf", "").split("_");
                            if (partes.length < 2)
                                return;

                            try {
                                LocalDate fechaArchivo = LocalDate.parse(partes[1],
                                        DateTimeFormatter.BASIC_ISO_DATE);
                                if ((fechaArchivo.isEqual(fechaInicio) || fechaArchivo.isAfter(fechaInicio))
                                        &&
                                        (fechaArchivo.isEqual(fechaFin) || fechaArchivo.isBefore(fechaFin))) {

                                    String url = baseUrl + "/api/rest/documentos/descargar/" + nombre;
                                    enlaces.add(url);
                                }
                            } catch (DateTimeParseException e) {
                                // ignora archivos mal nombrados
                            }
                        }
                    });

        } catch (IOException e) {
            logger.error("Error al listar archivos: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList("Error al leer los archivos"));
        }

        return ResponseEntity.ok(enlaces);
    }

    @GetMapping("/descargar/{nombreArchivo}")
    public ResponseEntity<Resource> descargarDocumento(@PathVariable String nombreArchivo) {
        try {
            Path path = Paths.get(rutaBase).resolve(nombreArchivo).normalize();
            Resource recurso = new UrlResource(path.toUri());

            if (!recurso.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + recurso.getFilename() + "\"")
                    .body(recurso);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Endpoint para subir archivos
    @PostMapping("/subir")
    public ResponseEntity<String> subirArchivo(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body("Archivo vacío");
        }

        try {
            String nombreLimpio = StringUtils.cleanPath(archivo.getOriginalFilename());
            Path rutaDestino = Paths.get(rutaBase).resolve(nombreLimpio);
            Files.copy(archivo.getInputStream(), rutaDestino, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok("Archivo subido correctamente: " + nombreLimpio);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al subir el archivo: " + e.getMessage());
        }
    }
}
