-- K'aaxPark - Esquema SQL de prueba
-- Motor sugerido: MySQL 8+
-- Este esquema replica en SQL la estructura funcional que despues puede
-- mapearse a colecciones de Firebase/Firestore.

DROP TABLE IF EXISTS solicitudes_recuperacion;
DROP TABLE IF EXISTS pagos;
DROP TABLE IF EXISTS estancias;
DROP TABLE IF EXISTS metricas_sustentabilidad;
DROP TABLE IF EXISTS tarifas;
DROP TABLE IF EXISTS cajones;
DROP TABLE IF EXISTS vehiculos;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    firebase_uid VARCHAR(128) NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    apellido_paterno VARCHAR(100) NULL,
    apellido_materno VARCHAR(100) NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NULL,
    telefono VARCHAR(20) NULL,
    rol ENUM('ADMIN', 'CLIENTE') NOT NULL DEFAULT 'CLIENTE',
    estado ENUM('ACTIVO', 'INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehiculos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    placa VARCHAR(15) NOT NULL UNIQUE,
    marca VARCHAR(50) NOT NULL,
    modelo VARCHAR(50) NULL,
    color VARCHAR(30) NOT NULL,
    tipo ENUM('SEDAN', 'SUV', 'PICKUP', 'MOTOCICLETA', 'OTRO') NOT NULL DEFAULT 'SEDAN',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehiculos_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE cajones (
    id INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nivel VARCHAR(20) NOT NULL,
    seccion VARCHAR(20) NULL,
    ubicacion_descripcion VARCHAR(100) NOT NULL,
    estado ENUM('DISPONIBLE', 'OCUPADO', 'RESERVADO', 'MANTENIMIENTO') NOT NULL DEFAULT 'DISPONIBLE',
    tiene_cargador BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tarifas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    tipo_cobro ENUM('MINUTO', 'HORA', 'DIA', 'EVENTO') NOT NULL DEFAULT 'MINUTO',
    monto DECIMAL(10,2) NOT NULL,
    iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    vigente_desde DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vigente_hasta DATETIME NULL
);

CREATE TABLE metricas_sustentabilidad (
    id INT AUTO_INCREMENT PRIMARY KEY,
    periodo_mes DATE NOT NULL UNIQUE,
    co2_toneladas DECIMAL(10,2) NOT NULL DEFAULT 0,
    arboles_equivalentes INT NOT NULL DEFAULT 0,
    energia_ahorrada_kwh DECIMAL(10,2) NOT NULL DEFAULT 0,
    ocupacion_promedio DECIMAL(5,2) NULL,
    tiempo_promedio_minutos INT NULL,
    usuarios_activos INT NULL
);

CREATE TABLE estancias (
    id INT AUTO_INCREMENT PRIMARY KEY,
    codigo_qr VARCHAR(100) NOT NULL UNIQUE,
    usuario_id INT NOT NULL,
    vehiculo_id INT NOT NULL,
    cajon_id INT NOT NULL,
    tarifa_id INT NOT NULL,
    estatus ENUM('ACTIVA', 'PAGADA', 'FINALIZADA', 'CANCELADA') NOT NULL DEFAULT 'ACTIVA',
    fecha_entrada DATETIME NOT NULL,
    fecha_salida DATETIME NULL,
    minutos_totales INT NULL,
    subtotal DECIMAL(10,2) NULL,
    iva DECIMAL(10,2) NULL,
    total DECIMAL(10,2) NULL,
    observaciones VARCHAR(255) NULL,
    CONSTRAINT fk_estancias_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_estancias_vehiculo
        FOREIGN KEY (vehiculo_id) REFERENCES vehiculos(id),
    CONSTRAINT fk_estancias_cajon
        FOREIGN KEY (cajon_id) REFERENCES cajones(id),
    CONSTRAINT fk_estancias_tarifa
        FOREIGN KEY (tarifa_id) REFERENCES tarifas(id)
);

CREATE TABLE pagos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    estancia_id INT NOT NULL,
    metodo ENUM('EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'DIGITAL') NOT NULL,
    referencia VARCHAR(100) NULL,
    estatus ENUM('PENDIENTE', 'PAGADO', 'RECHAZADO', 'REEMBOLSADO') NOT NULL DEFAULT 'PENDIENTE',
    fecha_pago DATETIME NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    iva DECIMAL(10,2) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    CONSTRAINT uq_pagos_estancia UNIQUE (estancia_id),
    CONSTRAINT fk_pagos_estancia
        FOREIGN KEY (estancia_id) REFERENCES estancias(id)
);

CREATE TABLE solicitudes_recuperacion (
    id INT AUTO_INCREMENT PRIMARY KEY,
    estancia_id INT NOT NULL,
    fecha_solicitud DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_entrega DATETIME NULL,
    estatus ENUM('SOLICITADA', 'EN_PROCESO', 'ENTREGADA', 'CANCELADA') NOT NULL DEFAULT 'SOLICITADA',
    observaciones VARCHAR(255) NULL,
    CONSTRAINT fk_solicitudes_estancia
        FOREIGN KEY (estancia_id) REFERENCES estancias(id)
);

CREATE INDEX idx_usuarios_rol_estado ON usuarios (rol, estado);
CREATE INDEX idx_vehiculos_usuario ON vehiculos (usuario_id);
CREATE INDEX idx_cajones_estado ON cajones (estado);
CREATE INDEX idx_estancias_usuario_fecha ON estancias (usuario_id, fecha_entrada);
CREATE INDEX idx_estancias_estatus_fecha ON estancias (estatus, fecha_entrada);
CREATE INDEX idx_pagos_estatus_fecha ON pagos (estatus, fecha_pago);
CREATE INDEX idx_solicitudes_estatus ON solicitudes_recuperacion (estatus);

-- Datos de prueba
INSERT INTO usuarios (
    firebase_uid, nombre, apellido_paterno, apellido_materno, email, password_hash,
    telefono, rol, estado
) VALUES
    ('uid-admin-001', 'Leslie Michelle', 'Canul', 'Pech', 'leslimichel471@gmail.com', NULL, '9991001001', 'ADMIN', 'ACTIVO'),
    ('uid-cli-001', 'Ana Sofia', 'Mendez', 'Chan', 'ana.sofia@kaaxpark.test', NULL, '9992003001', 'CLIENTE', 'ACTIVO'),
    ('uid-cli-002', 'Luis Fernando', 'Poot', 'Yam', 'luis.fernando@kaaxpark.test', NULL, '9992003002', 'CLIENTE', 'INACTIVO');

INSERT INTO vehiculos (
    usuario_id, placa, marca, modelo, color, tipo, activo
) VALUES
    (2, 'YUC-123-A', 'Toyota', 'Corolla', 'Gris', 'SEDAN', TRUE),
    (2, 'YUC-456-B', 'Nissan', 'Kicks', 'Blanco', 'SUV', TRUE),
    (3, 'YUC-789-C', 'Mazda', 'CX-5', 'Rojo', 'SUV', FALSE);

INSERT INTO cajones (
    codigo, nivel, seccion, ubicacion_descripcion, estado, tiene_cargador
) VALUES
    ('N1C1', 'Nivel 1', 'C1', 'Piso 1 - C1', 'DISPONIBLE', FALSE),
    ('N1C2', 'Nivel 1', 'C2', 'Piso 1 - C2', 'DISPONIBLE', FALSE),
    ('N2C1', 'Nivel 2', 'C1', 'Piso 2 - C1', 'DISPONIBLE', TRUE),
    ('N2C2', 'Nivel 2', 'C2', 'Piso 2 - C2', 'OCUPADO', FALSE),
    ('N3C1', 'Nivel 3', 'C1', 'Piso 3 - C1', 'OCUPADO', FALSE),
    ('N3C2', 'Nivel 3', 'C2', 'Piso 3 - C2', 'RESERVADO', FALSE),
    ('N4C1', 'Nivel 4', 'C1', 'Piso 4 - C1', 'MANTENIMIENTO', FALSE),
    ('N4C2', 'Nivel 4', 'C2', 'Piso 4 - C2', 'DISPONIBLE', TRUE);

INSERT INTO tarifas (
    nombre, tipo_cobro, monto, iva_porcentaje, activa, vigente_desde
) VALUES
    ('Tarifa general por minuto', 'MINUTO', 5.00, 18.00, TRUE, '2026-01-01 00:00:00');

INSERT INTO metricas_sustentabilidad (
    periodo_mes, co2_toneladas, arboles_equivalentes, energia_ahorrada_kwh,
    ocupacion_promedio, tiempo_promedio_minutos, usuarios_activos
) VALUES
    ('2026-06-01', 2.45, 12, 1245.00, 76.00, 198, 128);

INSERT INTO estancias (
    codigo_qr, usuario_id, vehiculo_id, cajon_id, tarifa_id, estatus,
    fecha_entrada, fecha_salida, minutos_totales, subtotal, iva, total, observaciones
) VALUES
    ('QR-EST-20260619-001', 2, 1, 5, 1, 'ACTIVA', '2026-06-19 08:15:00', NULL, NULL, NULL, NULL, NULL, 'Vehiculo en estancia actual'),
    ('QR-EST-20260618-001', 2, 2, 4, 1, 'PAGADA', '2026-06-18 09:00:00', '2026-06-18 11:45:00', 165, 825.00, 148.50, 973.50, 'Pago completado'),
    ('QR-EST-20260615-001', 3, 3, 2, 1, 'FINALIZADA', '2026-06-15 14:10:00', '2026-06-15 15:05:00', 55, 275.00, 49.50, 324.50, 'Salida registrada');

INSERT INTO pagos (
    estancia_id, metodo, referencia, estatus, fecha_pago, subtotal, iva, total
) VALUES
    (2, 'DIGITAL', 'PAY-20260618-001', 'PAGADO', '2026-06-18 11:50:00', 825.00, 148.50, 973.50),
    (3, 'TARJETA', 'PAY-20260615-001', 'PENDIENTE', NULL, 275.00, 49.50, 324.50);

INSERT INTO solicitudes_recuperacion (
    estancia_id, fecha_solicitud, fecha_entrega, estatus, observaciones
) VALUES
    (1, '2026-06-19 10:05:00', NULL, 'EN_PROCESO', 'Solicitud generada desde la app cliente');

-- Sugerencia de mapeo a Firebase:
-- usuarios
-- vehiculos
-- cajones
-- tarifas
-- estancias
-- pagos
-- solicitudes_recuperacion
-- metricas_sustentabilidad
