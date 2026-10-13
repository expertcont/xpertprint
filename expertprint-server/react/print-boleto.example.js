// Adaptar tu generador: antes de crear Blob, devolver también `bytes` de pdfDoc.save().
// La clave interna nunca debe aparecer en React.
export async function enviarBoletoAImpresion({ pdfBytes, idempotencyKey }) {
  const form = new FormData();
  form.append('pdf', new Blob([pdfBytes], { type: 'application/pdf' }), 'boleto.pdf');
  const response = await fetch('/print/boleto', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: form,
    credentials: 'include'
  });
  if (!response.ok) throw new Error(`No se pudo encolar boleto: HTTP ${response.status}`);
  return response.json();
}
