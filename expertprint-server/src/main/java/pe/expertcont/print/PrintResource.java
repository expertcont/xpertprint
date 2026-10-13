package pe.expertcont.print;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.sql.DataSource;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Path("/api")
public class PrintResource {
 @Inject DataSource ds;
 @ConfigProperty(name="expertprint.internal-key") String internalKey;
 @ConfigProperty(name="expertprint.storage-dir") String storageDir;

 public record Device(UUID id,String usuario,String documento,String agencia) {}
 public record Job(long id,String tipo,String impresora,String estado) {}
 public record Result(String estado,String error) {}
 private static String hash(String s) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
  catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 private void checkInternal(String key) {
  if (key==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),internalKey.getBytes(StandardCharsets.UTF_8)))
   throw new NotAuthorizedException("Clave interna invalida");
 }
 private Device device(String id,String token) throws SQLException {
  if (id==null || token==null) throw new NotAuthorizedException("Dispositivo sin credenciales");
  UUID uuid;
  try { uuid=UUID.fromString(id); } catch (IllegalArgumentException e) {throw new NotAuthorizedException("UUID invalido");}
  try (Connection c=ds.getConnection();PreparedStatement q=c.prepareStatement(
   "SELECT id_usuario,documento_id,id_punto_venta,token_hash FROM public.mve_transprint_device WHERE id_dispositivo=? AND activo=true")) {
   q.setObject(1,uuid);
   try(ResultSet r=q.executeQuery()) {
    if (!r.next() || !MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.UTF_8),r.getString(4).trim().getBytes(StandardCharsets.UTF_8)))
     throw new NotAuthorizedException("Token invalido");
    return new Device(uuid,r.getString(1),r.getString(2),r.getString(3));
   }
  }
 }
 private static String clean(String s,int max) {
  if(s==null || s.isBlank() || s.length()>max) throw new BadRequestException("Dato requerido o demasiado largo");
  return s.trim();
 }

 // Endpoint SOLO para Express. No exponer X-Internal-Key al navegador.
 @POST @Path("/internal/jobs") @Consumes("application/pdf") @Produces(MediaType.APPLICATION_JSON)
 public Response create(@HeaderParam("X-Internal-Key") String key,
  @HeaderParam("X-Usuario") String usuario,@HeaderParam("X-Documento") String documento,
  @HeaderParam("X-Agencia") String agencia,@HeaderParam("X-Periodo") String periodo,
  @HeaderParam("X-Tipo") String tipo,@HeaderParam("X-Referencia") String referencia,
  @HeaderParam("Idempotency-Key") String idem,byte[] pdf) throws Exception {
  checkInternal(key);
  usuario=clean(usuario,50); documento=clean(documento,20); agencia=clean(agencia,10);
  periodo=clean(periodo,7); tipo=clean(tipo,12); idem=clean(idem,150);
  if (!Set.of("BOLETO","MANIFIESTO").contains(tipo)) throw new BadRequestException("Tipo invalido");
  if (pdf==null || pdf.length<8 || pdf.length>2_000_000 || !new String(pdf,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))
   throw new BadRequestException("PDF invalido o demasiado grande");
  String impresora=tipo.equals("BOLETO")?"TERMICA":"A4";
  String ref=clean(referencia,1000);
  // La referencia debe ser JSON valido; PostgreSQL valida el tipo JSONB.
  Path dir=Path.of(storageDir).toAbsolutePath().normalize();Files.createDirectories(dir);
  Path file=dir.resolve(UUID.randomUUID()+".pdf");
  Files.write(file,pdf,StandardOpenOption.CREATE_NEW);
  try(Connection c=ds.getConnection()) {
   // Retornar el trabajo existente sin sobrescribir PDF: idempotencia por empresa.
   try(PreparedStatement q=c.prepareStatement("INSERT INTO public.mve_transprint (id_usuario,documento_id,id_punto_venta,tipo_documento,tipo_impresora,periodo,referencia,archivo_pdf,idempotency_key) VALUES (?,?,?,?,?,?,?::jsonb,?,?) ON CONFLICT (id_usuario,documento_id,idempotency_key) DO NOTHING RETURNING id_trabajo")) {
    q.setString(1,usuario);q.setString(2,documento);q.setString(3,agencia);q.setString(4,tipo);q.setString(5,impresora);q.setString(6,periodo);q.setString(7,ref);q.setString(8,file.toString());q.setString(9,idem);
    try(ResultSet r=q.executeQuery()) {if(r.next())return Response.status(201).entity(Map.of("id_trabajo",r.getLong(1),"estado","PENDIENTE")).build();}
   }
   Files.deleteIfExists(file);
   try(PreparedStatement q=c.prepareStatement("SELECT id_trabajo,estado FROM public.mve_transprint WHERE id_usuario=? AND documento_id=? AND idempotency_key=?")) {
    q.setString(1,usuario);q.setString(2,documento);q.setString(3,idem);
    try(ResultSet r=q.executeQuery()) {if(r.next())return Response.ok(Map.of("id_trabajo",r.getLong(1),"estado",r.getString(2),"repetido",true)).build();}
   }
   throw new ServerErrorException("No se pudo consultar trabajo",500);
  } catch(Exception e){Files.deleteIfExists(file);throw e;}
 }

 @GET @Path("/device/jobs") @Produces(MediaType.APPLICATION_JSON)
 public List<Job> pending(@HeaderParam("X-Device-Id") String id,@HeaderParam("X-Device-Token") String token) throws SQLException {
  Device d=device(id,token);List<Job> jobs=new ArrayList<>();
  try(Connection c=ds.getConnection();PreparedStatement q=c.prepareStatement(
   "SELECT id_trabajo,tipo_documento,tipo_impresora,estado FROM public.mve_transprint WHERE id_usuario=? AND documento_id=? AND id_punto_venta=? AND (estado='PENDIENTE' OR (estado='ASIGNADO' AND id_dispositivo=? AND lease_hasta>now())) ORDER BY creado LIMIT 30")) {
   q.setString(1,d.usuario);q.setString(2,d.documento);q.setString(3,d.agencia);q.setObject(4,d.id);
   try(ResultSet r=q.executeQuery()){while(r.next())jobs.add(new Job(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)));}
  }return jobs;
 }

 @POST @Path("/device/jobs/{jobId}/claim") @Produces(MediaType.APPLICATION_JSON)
 public Response claim(@HeaderParam("X-Device-Id") String id,@HeaderParam("X-Device-Token") String token,@PathParam("jobId") long jobId) throws SQLException {
  Device d=device(id,token);
  try(Connection c=ds.getConnection();PreparedStatement q=c.prepareStatement(
   "UPDATE public.mve_transprint SET estado='ASIGNADO',id_dispositivo=?,lease_hasta=now()+interval '3 minutes',intentos=intentos+1,actualizado=now() WHERE id_trabajo=? AND id_usuario=? AND documento_id=? AND id_punto_venta=? AND estado='PENDIENTE' RETURNING id_trabajo")) {
   q.setObject(1,d.id);q.setLong(2,jobId);q.setString(3,d.usuario);q.setString(4,d.documento);q.setString(5,d.agencia);
   try(ResultSet r=q.executeQuery()) {return r.next()?Response.ok(Map.of("id_trabajo",jobId,"estado","ASIGNADO")).build():Response.status(409).build();}
  }
 }

 @GET @Path("/device/jobs/{jobId}/pdf") @Produces("application/pdf")
 public Response pdf(@HeaderParam("X-Device-Id") String id,@HeaderParam("X-Device-Token") String token,@PathParam("jobId") long jobId) throws Exception {
  Device d=device(id,token);
  try(Connection c=ds.getConnection();PreparedStatement q=c.prepareStatement(
   "SELECT archivo_pdf FROM public.mve_transprint WHERE id_trabajo=? AND id_usuario=? AND documento_id=? AND id_punto_venta=? AND id_dispositivo=? AND estado IN ('ASIGNADO','EN_COLA','ENVIADO','INCIERTO')")) {
   q.setLong(1,jobId);q.setString(2,d.usuario);q.setString(3,d.documento);q.setString(4,d.agencia);q.setObject(5,d.id);
   try(ResultSet r=q.executeQuery()) {
    if(!r.next())throw new NotFoundException();
    Path root=Path.of(storageDir).toAbsolutePath().normalize();Path f=Path.of(r.getString(1)).toAbsolutePath().normalize();
    if(!f.startsWith(root))throw new ForbiddenException();
    return Response.ok(Files.readAllBytes(f)).header("Content-Disposition","attachment; filename=expertprint-"+jobId+".pdf").build();
   }
  }
 }

 @POST @Path("/device/jobs/{jobId}/result") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
 public Response result(@HeaderParam("X-Device-Id") String id,@HeaderParam("X-Device-Token") String token,@PathParam("jobId") long jobId,Result result) throws SQLException {
  Device d=device(id,token);
  if(result==null || !Set.of("EN_COLA","ENVIADO","ERROR","INCIERTO").contains(result.estado()))throw new BadRequestException("Estado invalido");
  String allowed=result.estado().equals("EN_COLA")?"'ASIGNADO'": "'ASIGNADO','EN_COLA'";
  String sql="UPDATE public.mve_transprint SET estado=?,ultimo_error=?,actualizado=now(),lease_hasta=NULL WHERE id_trabajo=? AND id_usuario=? AND documento_id=? AND id_punto_venta=? AND id_dispositivo=? AND estado IN ("+allowed+") RETURNING id_trabajo";
  try(Connection c=ds.getConnection();PreparedStatement q=c.prepareStatement(sql)) {
   q.setString(1,result.estado());q.setString(2,result.error()==null?null:result.error().substring(0,Math.min(1000,result.error().length())));
   q.setLong(3,jobId);q.setString(4,d.usuario);q.setString(5,d.documento);q.setString(6,d.agencia);q.setObject(7,d.id);
   try(ResultSet r=q.executeQuery()){return r.next()?Response.ok(Map.of("estado",result.estado())).build():Response.status(409).build();}
  }
 }
}
