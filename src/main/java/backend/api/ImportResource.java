package backend.api;

import backend.entities.ImportOperation;
import backend.repository.ImportRepository;
import backend.service.ImportService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.List;

@Path("/imports")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    private ImportService importService;

    @Inject
    private ImportRepository importRepository;

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
}
