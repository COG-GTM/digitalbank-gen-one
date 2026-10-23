package io.digisic.bank.test.junit.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the change-management controls for ITGC-CM-07 (Jira EM-137): CODEOWNERS must give
 * money-movement and security code a dedicated owner, the PR template must capture change risk
 * and reviewer attestation, and the branch-protection settings an admin must enable are documented.
 *
 * <p>Plain JUnit 5 with no Spring context so {@code scripts/governance/check-codeowners.sh} can
 * run it without a full Maven build.
 */
public class GovernanceControlsTest {

	static final String PAYMENTS_OWNERS = "@COG-GTM/digitalbank-payments-owners";
	static final String SECURITY_OWNERS = "@COG-GTM/digitalbank-security-owners";

	private static final String[] MODULES = { "bank", "credit" };

	private static final Pattern OWNER_FORMAT =
			Pattern.compile("@[A-Za-z0-9-]+(/[A-Za-z0-9._-]+)?|[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private static final Pattern TLS_MATERIAL = Pattern.compile(".*\\.(jks|p12|pfx|pem|key|crt)");

	private final Path root = repoRoot();

	@Test
	public void codeownersAssignsPaymentsOwnersToEveryMoneyMovementFile() throws IOException {
		assertDedicatedOwner("service", PAYMENTS_OWNERS);
		assertDedicatedOwner("controller", PAYMENTS_OWNERS);
	}

	private static boolean isAuthenticationClass(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.contains("Authentication") || name.contains("Security");
	}

	@Test
	public void codeownersAssignsSecurityOwnersToEverySecurityAndConfigFile() throws IOException {
		assertDedicatedOwner("security", SECURITY_OWNERS);
		assertDedicatedOwner("config", SECURITY_OWNERS);
	}

	@Test
	public void codeownersGivesSecurityOwnersSoleOwnershipOfAuthenticationAndTlsFiles() throws IOException {
		Codeowners codeowners = loadCodeowners();
		List<String> files = new ArrayList<>();
		for (String module : MODULES) {
			files.addAll(filesUnder(module + "/src/main/java", GovernanceControlsTest::isAuthenticationClass));
			files.addAll(filesUnder(module + "/src/main/resources", p -> p.endsWith("/server.xml")
					|| p.contains("/keystore/") || TLS_MATERIAL.matcher(p).matches()));
		}
		assertTrue(files.stream().anyMatch(f -> f.endsWith("/UserSecurityService.java")), "expected UserSecurityService");
		assertTrue(files.stream().anyMatch(f -> f.endsWith("/AuthenticationController.java")), "expected AuthenticationController");
		assertTrue(files.stream().anyMatch(f -> f.endsWith(".jks")), "expected a keystore");
		List<String> uncovered = new ArrayList<>();
		for (String file : files) {
			if (!codeowners.ownersOf(file).equals(Collections.singletonList(SECURITY_OWNERS))) {
				uncovered.add(file + " -> " + codeowners.ownersOf(file));
			}
		}
		assertTrue(uncovered.isEmpty(), "authentication/TLS files must be owned by " + SECURITY_OWNERS
				+ " alone (any listed owner can approve):\n  " + String.join("\n  ", uncovered));
		assertTrue(codeowners.ownersOf("deploy/certs/new.pem").contains(SECURITY_OWNERS), "*.pem must be security-owned");
	}

	@Test
	public void codeownersProtectsTheGovernanceControlsThemselves() throws IOException {
		Codeowners codeowners = loadCodeowners();
		for (String path : Arrays.asList(".github/CODEOWNERS", ".github/pull_request_template.md",
				".github/workflows/governance-checks.yml", "docs/governance/branch-protection.md",
				"scripts/governance/check-codeowners.sh",
				"bank/src/test/java/io/digisic/bank/test/junit/governance/GovernanceControlsTest.java")) {
			assertTrue(codeowners.ownersOf(path).contains(SECURITY_OWNERS),
					path + " must be owned by " + SECURITY_OWNERS + " but is owned by " + codeowners.ownersOf(path));
		}
	}

	@Test
	public void codeownersHasDefaultOwnerAndOnlyWellFormedOwners() throws IOException {
		Codeowners codeowners = loadCodeowners();
		assertFalse(codeowners.ownersOf("README.md").isEmpty(), "CODEOWNERS needs a default '*' rule");
		for (Rule rule : codeowners.rules) {
			assertFalse(rule.owners.isEmpty(), "CODEOWNERS line " + rule.line + " has no owner: " + rule.pattern);
			for (String owner : rule.owners) {
				assertTrue(OWNER_FORMAT.matcher(owner).matches(),
						"CODEOWNERS line " + rule.line + " has malformed owner '" + owner + "'");
			}
		}
	}

	@Test
	public void pullRequestTemplateCapturesChangeRiskAndReviewerAttestation() throws IOException {
		String template = read(".github/pull_request_template.md").toLowerCase(Locale.ROOT);
		for (String required : Arrays.asList("## change risk", "risk level", "money movement", "rollback plan",
				"jira", "## reviewer attestation", "i am not the author", "reviewed the full diff",
				"status checks have passed", "named corporate identity", "signed")) {
			assertTrue(template.contains(required), "PR template is missing '" + required + "'");
		}
	}

	@Test
	public void branchProtectionSettingsAreDocumented() throws IOException {
		String doc = read("docs/governance/branch-protection.md");
		for (String required : Arrays.asList("Require a pull request before merging", "Require approvals",
				"Dismiss stale pull request approvals when new commits are pushed",
				"Require review from Code Owners", "Require approval of the most recent reviewable push",
				"Require status checks to pass before merging", "Require signed commits",
				"Do not allow bypassing the above settings", "CODEOWNERS coverage (ITGC-CM-07)",
				"user.email", "commit.gpgsign")) {
			assertTrue(doc.contains(required), "docs/governance/branch-protection.md is missing '" + required + "'");
		}
	}

	@Test
	public void matcherFollowsGitHubCodeownersSemantics() {
		Codeowners codeowners = Codeowners.parse(Arrays.asList(
				"* @org/default",
				"/bank/src/main/java/**/service/ @org/payments",
				"*.properties @org/secrets",
				"/docs/ @org/docs"));
		assertTrue(codeowners.ownersOf("bank/src/main/java/io/x/service/A.java").contains("@org/payments"));
		assertTrue(codeowners.ownersOf("bank/src/main/java/service/A.java").contains("@org/payments"));
		assertTrue(codeowners.ownersOf("bank/src/main/java/io/x/service/sub/B.java").contains("@org/payments"));
		assertTrue(codeowners.ownersOf("credit/src/main/java/io/x/service/A.java").contains("@org/default"));
		assertTrue(codeowners.ownersOf("bank/src/main/java/io/x/services/A.java").contains("@org/default"));
		assertTrue(codeowners.ownersOf("a/b/app.properties").contains("@org/secrets"));
		assertTrue(codeowners.ownersOf("docs/x/y.md").contains("@org/docs"));
		assertTrue(codeowners.ownersOf("src/docs/y.md").contains("@org/default"));
	}

	private void assertDedicatedOwner(String packageDir, String requiredOwner) throws IOException {
		Codeowners codeowners = loadCodeowners();
		List<String> files = sourceFilesUnder(packageDir);
		assertFalse(files.isEmpty(), "no source files found under **/" + packageDir + "/");
		List<String> uncovered = new ArrayList<>();
		for (String file : files) {
			if (PAYMENTS_OWNERS.equals(requiredOwner) && isAuthenticationClass(file)) {
				continue;
			}
			if (!codeowners.ownersOf(file).contains(requiredOwner)) {
				uncovered.add(file + " -> " + codeowners.ownersOf(file));
			}
		}
		assertTrue(uncovered.isEmpty(), "CODEOWNERS does not assign " + requiredOwner + " to:\n  "
				+ String.join("\n  ", uncovered));
	}

	private List<String> sourceFilesUnder(String packageDir) throws IOException {
		List<String> files = new ArrayList<>();
		for (String module : MODULES) {
			files.addAll(filesUnder(module + "/src/main/java", p -> p.contains("/" + packageDir + "/")));
		}
		Collections.sort(files);
		return files;
	}

	private List<String> filesUnder(String relativeDir, Predicate<String> filter) throws IOException {
		Path dir = root.resolve(relativeDir);
		if (!Files.isDirectory(dir)) {
			return Collections.emptyList();
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.filter(Files::isRegularFile)
					.map(p -> root.relativize(p).toString().replace('\\', '/'))
					.filter(filter)
					.sorted()
					.collect(Collectors.toList());
		}
	}

	private Codeowners loadCodeowners() throws IOException {
		Path file = root.resolve(".github/CODEOWNERS");
		if (!Files.isRegularFile(file)) {
			fail("missing .github/CODEOWNERS: money-movement and security code has no required reviewer");
		}
		return Codeowners.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
	}

	private String read(String relativePath) throws IOException {
		Path file = root.resolve(relativePath);
		if (!Files.isRegularFile(file)) {
			fail("missing " + relativePath);
		}
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	private static Path repoRoot() {
		String configured = System.getProperty("repo.root");
		if (configured != null && !configured.isEmpty()) {
			return Paths.get(configured).toAbsolutePath().normalize();
		}
		Path dir = Paths.get("").toAbsolutePath();
		while (dir != null) {
			if (Files.isRegularFile(dir.resolve("bank/pom.xml"))) {
				return dir;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("cannot locate repository root from " + Paths.get("").toAbsolutePath());
	}

	static final class Rule {
		final int line;
		final String pattern;
		final Pattern regex;
		final List<String> owners;

		Rule(int line, String pattern, List<String> owners) {
			this.line = line;
			this.pattern = pattern;
			this.regex = toRegex(pattern);
			this.owners = owners;
		}

		/** gitignore-style matching as used by GitHub CODEOWNERS. */
		static Pattern toRegex(String pattern) {
			boolean directoryOnly = pattern.endsWith("/");
			String p = directoryOnly ? pattern.substring(0, pattern.length() - 1) : pattern;
			boolean anchored = p.startsWith("/") || p.contains("/");
			if (p.startsWith("/")) {
				p = p.substring(1);
			}
			StringBuilder regex = new StringBuilder(anchored ? "" : "(?:.*/)?");
			for (int i = 0; i < p.length(); i++) {
				char c = p.charAt(i);
				if (c == '*' && i + 1 < p.length() && p.charAt(i + 1) == '*') {
					boolean slashAfter = i + 2 < p.length() && p.charAt(i + 2) == '/';
					regex.append(slashAfter ? "(?:.*/)?" : ".*");
					i += slashAfter ? 2 : 1;
				} else if (c == '*') {
					regex.append("[^/]*");
				} else if (c == '?') {
					regex.append("[^/]");
				} else {
					regex.append(Pattern.quote(String.valueOf(c)));
				}
			}
			regex.append(directoryOnly ? "/.*" : "(?:/.*)?");
			return Pattern.compile(regex.toString());
		}
	}

	static final class Codeowners {
		final List<Rule> rules;

		private Codeowners(List<Rule> rules) {
			this.rules = rules;
		}

		static Codeowners parse(List<String> lines) {
			List<Rule> rules = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i).trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				List<String> tokens = Arrays.stream(line.split("\\s+"))
						.filter(t -> !t.isEmpty())
						.collect(Collectors.toList());
				int comment = tokens.indexOf("#");
				List<String> effective = comment >= 0 ? tokens.subList(0, comment) : tokens;
				rules.add(new Rule(i + 1, effective.get(0), new ArrayList<>(effective.subList(1, effective.size()))));
			}
			return new Codeowners(rules);
		}

		/** Owners from the last matching rule, which is how GitHub resolves CODEOWNERS. */
		List<String> ownersOf(String path) {
			List<String> owners = Collections.emptyList();
			for (Rule rule : rules) {
				if (rule.regex.matcher(path).matches()) {
					owners = rule.owners;
				}
			}
			return owners;
		}
	}
}
