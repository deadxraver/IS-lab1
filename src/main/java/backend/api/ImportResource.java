package backend.api;

import backend.entities.ImportOperation;
import backend.repository.ImportRepository;
import backend.service.ImportService;
import backend.service.MinIOService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;
import java.util.List;

@Path("/imports")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    private ImportService importService;

    @Inject
    private ImportRepository importRepository;

    @Inject
    private MinIOService minIOService;

    @POST
    @Path("/routes")
    @Consumes(MediaType.APPLICATION_XML)
    public Response importRoutes(@HeaderParam("X-User") String user,
                                 InputStream xmlStream) {
        String username = user == null ? "anonymous" : user;
        try {
            if (xmlStream == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("request body (xml) is required").build();
            }
            importService.importFromXml(xmlStream, username);
            return Response.ok("Import finished (check history)").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Import failed: " + e.getMessage()).build();
        }
    }

    @GET
    public Response getImportHistory(@HeaderParam("X-User") String user) {
        String username = user == null ? "anonymous" : user;
        try {
            List<ImportOperation> ops = importRepository.findByUser(username);
            return Response.ok(ops).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to get import history: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}/file")
    @Produces(MediaType.APPLICATION_XML)
    public Response downloadImportFile(@PathParam("id") Long id, @HeaderParam("X-User") String user) {
        String username = user == null ? "anonymous" : user;
        try {
            ImportOperation operation = importRepository.findById(id);
            
            if (operation == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Import operation not found").build();
            }
            
            // Проверяем, что операция принадлежит текущему пользователю
            if (!operation.getUser().equals(username)) {
                return Response.status(Response.Status.FORBIDDEN).entity("Access denied").build();
            }
            
            if (operation.getFileObjectName() == null || operation.getFileObjectName().trim().isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).entity("File not available for this import operation").build();
            }
            
            InputStream fileStream = minIOService.downloadFile(operation.getFileObjectName());
            
            StreamingOutput stream = output -> {
                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    output.flush();
                } finally {
                    fileStream.close();
                }
            };
            
            return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"import-" + id + ".xml\"")
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to download file: " + e.getMessage()).build();
        }
    }
}
