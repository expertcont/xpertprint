// EJEMPLO: montar DESPUÉS del middleware Auth0 y la verificación real de empresa/agencia.
// npm i multer
const express = require('express');
const multer = require('multer');
const router = express.Router();
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 2_000_000 } });
router.post('/print/boleto', upload.single('pdf'), async (req, res) => {
  // REEMPLAZAR por validación real Auth0 + pertenencia a empresa/agencia.
  // Nunca aceptar id_usuario/documento_id/id_punto_venta del navegador sin validar.
  const autorizado = req.usuarioAutorizadoParaImprimir; // Placeholder deliberado
  if (!autorizado) return res.status(403).json({ error: 'Sin autorización' });
  if (!req.file) return res.status(400).json({ error: 'Falta PDF' });
  const { id_usuario, documento_id, id_punto_venta, periodo, referencia } = autorizado;
  const key = req.get('Idempotency-Key');
  if (!key) return res.status(400).json({ error: 'Falta Idempotency-Key' });
  try {
    const response = await fetch(`${process.env.EXPERTPRINT_URL}/api/internal/jobs`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/pdf', 'X-Internal-Key': process.env.EXPERTPRINT_INTERNAL_KEY,
        'X-Usuario': id_usuario, 'X-Documento': documento_id, 'X-Agencia': id_punto_venta,
        'X-Periodo': periodo, 'X-Tipo': 'BOLETO', 'X-Referencia': JSON.stringify(referencia),
        'Idempotency-Key': key
      },
      body: req.file.buffer
    });
    res.status(response.status).send(await response.text());
  } catch(e) { res.status(502).json({ error: 'Servicio de impresión no disponible' }); }
});
module.exports = router;
