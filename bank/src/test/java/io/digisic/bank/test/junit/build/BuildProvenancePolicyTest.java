package io.digisic.bank.test.junit.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the build-provenance controls (ITGC-CM-08 / ITGC-SDLC-09): CI builds from source,
 * images are built in-container, and deployments use immutable image references.
 * Set -Drepo.root=... to evaluate a different checkout (defaults to the parent of bank/).
 */
public class BuildProvenancePolicyTest {

	private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*image:\\s*[\"']?([^\\s\"'#]+)", Pattern.MULTILINE);

	private final Path root = Paths.get(System.getProperty("repo.root", "..")).toAbsolutePath().normalize();

	private String read(String relative) throws IOException {
		Path file = root.resolve(relative);
		assertTrue(Files.isRegularFile(file), "missing " + relative + " under " + root);
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	@Test
	public void ciWorkflowBuildsTestsScansAndTagsImagesWithCommitSha() throws IOException {
		String ci = read(".github/workflows/ci.yml");

		assertTrue(ci.contains("pull_request"), "CI must run on pull requests");
		assertTrue(ci.contains("push:"), "CI must run on push");
		assertTrue(ci.contains("./mvnw") && ci.contains("verify"), "CI must run mvnw verify");
		assertTrue(ci.contains("bank") && ci.contains("credit"), "CI must build bank and credit");
		assertTrue(ci.contains("dependency-check"), "CI must run OWASP dependency-check (SCA)");
		assertTrue(ci.contains("failBuildOnCVSS=7"), "SCA must fail on high/critical findings");
		assertTrue(ci.contains("gitleaks"), "CI must run secret scanning");
		assertTrue(ci.contains("github.sha"), "images must be tagged with the commit SHA");
	}

	@Test
	public void dockerImagesAreBuiltFromSourceInMultiStageBuilds() throws IOException {
		for (String module : new String[] {"bank", "credit"}) {
			String dockerfile = read(module + "/Dockerfile");
			long stages = dockerfile.lines().filter(l -> l.trim().toUpperCase().startsWith("FROM ")).count();
			assertTrue(stages >= 2, module + "/Dockerfile must be multi-stage");
			assertTrue(dockerfile.contains("mvnw"), module + "/Dockerfile must build the WAR with the Maven wrapper");

			List<String> localArtifactCopies = dockerfile.lines()
					.filter(l -> l.trim().toUpperCase().startsWith("COPY") && !l.contains("--from=") && l.contains("target/"))
					.collect(Collectors.toList());
			assertTrue(localArtifactCopies.isEmpty(),
					module + "/Dockerfile copies pre-built artifacts from a local target/: " + localArtifactCopies);
		}
	}

	@Test
	public void deploymentManifestsUseImmutableImageReferences() throws IOException {
		Path deploy = root.resolve("deploy");
		assertTrue(Files.isDirectory(deploy), "missing deploy/ under " + root);

		List<String> mutable = new ArrayList<>();
		try (Stream<Path> files = Files.walk(deploy)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
					.collect(Collectors.toList())) {
				Matcher m = IMAGE_LINE.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				while (m.find()) {
					String image = m.group(1);
					String lastSegment = image.substring(image.lastIndexOf('/') + 1);
					if (image.endsWith(":latest") || !(lastSegment.contains(":") || image.contains("@sha256:"))) {
						mutable.add(root.relativize(file) + " -> " + image);
					}
				}
			}
		}
		assertTrue(mutable.isEmpty(), "mutable image references (use a pinned tag/digest): " + mutable);
	}
}
