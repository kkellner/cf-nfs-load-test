package net.hyperboard;

import java.io.File;
import java.nio.file.Files;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Health indicator for volume-services (NFS) on cloud foundry. Verify that
 * directory exists and it at least readable.
 * 
 * Indicator documentation:
 * https://blog.jayway.com/2014/07/22/spring-boot-custom-healthindicator/
 * 
 * @author kkellner
 *
 */
@Component
public class NfsMountIndicator extends AbstractHealthIndicator {

	private static String ENV_VCAP_SERVICES = "VCAP_SERVICES";
	private static String ENV_NFS = "nfs";

	private static Status NO_NFS_BINDING = new Status("DOWN", "no nfs service binding");

	@Autowired
	private Environment env;

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {

		String jsonStr = env.getProperty(ENV_VCAP_SERVICES);
		if (null == jsonStr) {
			builder.status(NO_NFS_BINDING);
			// builder.down();
			return;
		}

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonRootNode = objectMapper.readTree(jsonStr);

		boolean running = true;

		// Get a list of NFS service JSON nodes
		JsonNode nfsNodes = jsonRootNode.get(ENV_NFS);
		if (null == nfsNodes) {
			builder.status(NO_NFS_BINDING);
			return;
		}

		for (JsonNode nfsNode : nfsNodes) {

			String serviceName = nfsNode.get("name").asText();
			String containerDir = null;

			// Get a list of volume_mounts node (in reality there is always 1)
			for (JsonNode mountNode : nfsNode.get("volume_mounts")) {

				try {
					containerDir = mountNode.get("container_dir").asText();
					String detailKey = serviceName + ":" + containerDir;

					if (Files.isReadable(new File(containerDir).toPath())) {
						builder.up().withDetail(detailKey, "Read permission enabled.");
					} else {
						running = false;
						builder.down().withDetail(detailKey, "No read permission.");
					}
				} catch (Exception e) {
					running = false;
					builder.down().withDetail(containerDir, "Problems accessing nfs mount.");
				}
			}
		}

		if (running) {
			builder.up();
		} else {
			builder.down();
		}
	}
}
