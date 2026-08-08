package com.testforge.backend.analysis.scan;

import com.testforge.backend.analysis.entity.Severity;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The catalogue of static checks QPilot runs against uploaded source code.
 *
 * <p>These are pattern-based checks over real file contents, not a full dataflow analysis — so each
 * rule is written to be conservative and to carry the matched line as evidence, letting the user
 * confirm or dismiss it. Where a pattern is prone to firing on placeholders (a documented API-key
 * example, an environment-variable lookup) the rule declares a {@code falsePositiveFilter} rather
 * than reporting a finding the user will rightly distrust.
 *
 * <p>Deliberately absent: dependency CVE matching. That needs an advisory database QPilot does not
 * ship, and guessing would be worse than saying so — the engine reports it as an unavailable check.
 */
public final class SecurityRules {

    private static final Set<String> JVM = Set.of("java", "kt", "scala");
    private static final Set<String> JS = Set.of("js", "jsx", "ts", "tsx", "vue", "svelte");
    private static final Set<String> PY = Set.of("py");
    private static final Set<String> ANY = Set.of();

    /** Common ways a "secret" is actually a placeholder or an env lookup rather than a real credential. */
    private static final Pattern PLACEHOLDER_SECRET = Pattern.compile(
            "(?i)(process\\.env|System\\.getenv|os\\.environ|getenv|\\$\\{|\\{\\{|<[A-Z_]+>|"
                    + "your[_-]?|example|sample|placeholder|changeme|change[_-]me|dummy|xxx+|\\*{3,}|"
                    + "todo|redacted|insert[_-]?your|test[_-]?key|fake)");

    private SecurityRules() {
    }

    public static final List<SecurityRule> ALL = List.of(

            // ─── Credential exposure ────────────────────────────────────────────────
            rule("SEC-001", "HARDCODED_CREDENTIAL", Severity.HIGH,
                    "(?i)\\b(api[_-]?key|apikey|secret[_-]?key|client[_-]?secret|auth[_-]?token|"
                            + "access[_-]?token|password|passwd|pwd)\\b\\s*[:=]\\s*[\"'][^\"'\\s]{8,}[\"']",
                    "A credential-looking literal is assigned directly in source. Anything committed to a "
                            + "repository must be treated as public — secrets in source are readable by everyone with "
                            + "repository access and survive in git history even after removal.",
                    "Move the value to an environment variable or secret manager and read it at runtime. "
                            + "Rotate the exposed credential, since removing it from the current file does not "
                            + "invalidate it or erase it from version-control history.",
                    ANY, PLACEHOLDER_SECRET),

            rule("SEC-002", "PRIVATE_KEY_MATERIAL", Severity.CRITICAL,
                    "-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----",
                    "An embedded PEM private key block was found. This is signing/decryption material: "
                            + "possession of it is equivalent to possession of the identity it represents.",
                    "Remove the key from the repository, rotate/reissue it immediately, and load key material "
                            + "from a mounted secret or key-management service at runtime.",
                    ANY, null),

            rule("SEC-003", "CLOUD_ACCESS_KEY", Severity.CRITICAL,
                    "\\b(AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16})\\b",
                    "An AWS access key ID pattern was found in source. These are routinely harvested by "
                            + "automated scanners within minutes of being pushed to a public repository.",
                    "Deactivate and delete the key in IAM, then supply credentials via instance roles or "
                            + "environment configuration instead of source.",
                    ANY, null),

            rule("SEC-004", "PRIVATE_TOKEN_LITERAL", Severity.HIGH,
                    "\\b(gh[pousr]_[A-Za-z0-9]{16,}|xox[baprs]-[A-Za-z0-9-]{10,}|sk_live_[A-Za-z0-9]{10,}|"
                            + "AIza[0-9A-Za-z_\\-]{30,})\\b",
                    "A provider-issued token literal (GitHub / Slack / Stripe / Google API) was found in source.",
                    "Revoke the token at the issuing provider and load it from configuration at runtime.",
                    ANY, null),

            // ─── Injection ──────────────────────────────────────────────────────────
            rule("SEC-010", "SQL_INJECTION", Severity.HIGH,
                    // The string-content class is per-delimiter ([^"] inside a double-quoted literal) rather
                    // than [^"'] for both. SQL routinely contains apostrophes — WHERE id = '" + id + "' — and
                    // excluding them made this rule silently miss the single most common injection shape.
                    "(?i)(?:executeQuery|executeUpdate|execute|createQuery|createNativeQuery|rawQuery|"
                            + "prepareStatement|query)\\s*\\(\\s*"
                            + "(?:\"[^\"]*(?:select|insert|update|delete|where)[^\"]*\"|"
                            + "'[^']*(?:select|insert|update|delete|where)[^']*')\\s*\\+",
                    "A SQL statement is built by concatenating a string literal with a variable. If any part of "
                            + "that variable is attacker-influenced, the query structure itself can be rewritten.",
                    "Use a parameterized statement (JDBC PreparedStatement placeholders, JPA named parameters, "
                            + "or the ORM's query builder) so user input is bound as data and can never be parsed as SQL.",
                    ANY, null),

            rule("SEC-010B", "SQL_INJECTION", Severity.HIGH,
                    "(?i)\\b(?:sql|query|stmt|statement|hql|jpql)\\w*\\s*=\\s*"
                            + "\"[^\"]*(?:select|insert|update|delete)[^\"]*\"\\s*\\+",
                    "A SQL statement is assembled into a variable by concatenating a literal with a value. Even "
                            + "when the execution call sits elsewhere, the statement text is already "
                            + "attacker-influenced by the time it reaches the database.",
                    "Build the statement with bind-parameter placeholders and pass the values separately, so the "
                            + "database receives them as data rather than as executable statement text.",
                    ANY, null),

            rule("SEC-011", "SQL_INJECTION", Severity.HIGH,
                    "(?i)(?:execute|executemany|cursor\\.execute)\\s*\\(\\s*f[\"']|"
                            + "(?i)(?:select|insert into|update|delete from)\\s+.*%\\s*\\(?\\s*[a-z_]+\\s*\\)?\\s*[\"']?\\s*%",
                    "A SQL statement is assembled with string interpolation (f-string or % formatting) rather "
                            + "than parameter binding, so user input becomes part of the statement text.",
                    "Pass values as the second argument to cursor.execute() using placeholders, letting the "
                            + "database driver escape them.",
                    PY, null),

            rule("SEC-012", "COMMAND_INJECTION", Severity.HIGH,
                    "Runtime\\.getRuntime\\(\\)\\.exec\\(|new\\s+ProcessBuilder\\(",
                    "The application spawns an operating-system process. If any argument derives from request "
                            + "input, this becomes remote command execution.",
                    "Avoid shell invocation where a library call exists. If a process is required, pass arguments "
                            + "as a separate array (never a concatenated command line) and validate them against an allow-list.",
                    JVM, null),

            rule("SEC-013", "COMMAND_INJECTION", Severity.HIGH,
                    "os\\.system\\s*\\(|subprocess\\.(?:call|run|Popen|check_output)\\s*\\([^)]*shell\\s*=\\s*True",
                    "A shell is invoked to run a command (os.system, or subprocess with shell=True). With "
                            + "shell=True the string is parsed by the shell, so metacharacters in any interpolated "
                            + "value are executed.",
                    "Call subprocess with shell=False and an argument list, so arguments are passed to execve "
                            + "directly and shell metacharacters carry no meaning.",
                    PY, null),

            rule("SEC-014", "COMMAND_INJECTION", Severity.HIGH,
                    "child_process\\.exec\\s*\\(|require\\(['\"]child_process['\"]\\)\\.exec\\s*\\(",
                    "child_process.exec() runs its argument through a shell. Any interpolated value can inject "
                            + "additional commands.",
                    "Use execFile() or spawn() with an argument array instead of exec(), so no shell is involved.",
                    JS, null),

            rule("SEC-015", "CODE_INJECTION", Severity.HIGH,
                    "\\beval\\s*\\(|new\\s+Function\\s*\\(",
                    "Dynamic code evaluation (eval / new Function) is present. Any attacker-influenced input "
                            + "reaching it becomes arbitrary code execution in the application's context.",
                    "Replace dynamic evaluation with explicit parsing — JSON.parse for data, a lookup map for "
                            + "dispatch, or a purpose-built expression library with a restricted grammar.",
                    JS, null),

            rule("SEC-016", "XSS_SINK", Severity.MEDIUM,
                    "dangerouslySetInnerHTML|\\.innerHTML\\s*=|document\\.write\\s*\\(|v-html\\s*=",
                    "Markup is injected into the DOM without escaping. If the injected value contains "
                            + "user-controlled content, script can execute in the victim's session.",
                    "Render text through normal data binding (which escapes automatically). Where HTML really "
                            + "must be rendered, sanitize it first with a maintained sanitizer such as DOMPurify.",
                    JS, null),

            rule("SEC-017", "PATH_TRAVERSAL", Severity.MEDIUM,
                    "(?i)(?:new\\s+File|Paths\\.get|FileInputStream|readFile|readFileSync|open)\\s*\\("
                            + "[^)]*(?:request|req\\.|params|query|getParameter|user[Ii]nput|filename|file_?name)",
                    "A filesystem path is built from request-supplied input. Sequences such as ../ can escape "
                            + "the intended directory and read or overwrite unrelated files.",
                    "Resolve the path against a fixed base directory, then verify the normalized result still "
                            + "starts with that base before opening it. Prefer server-generated identifiers over "
                            + "client-supplied file names.",
                    ANY, null),

            rule("SEC-018", "SSRF", Severity.MEDIUM,
                    "(?i)(?:HttpRequest\\.newBuilder\\(\\)\\.uri|new\\s+URL|restTemplate\\.(?:get|post)|"
                            + "axios\\.(?:get|post)|requests\\.(?:get|post)|fetch)\\s*\\([^)]*"
                            + "(?:request\\.get|req\\.body|req\\.query|params\\[|getParameter)",
                    "An outbound HTTP request targets a URL derived from request input. This lets a caller "
                            + "make the server request internal addresses (cloud metadata endpoints, internal admin "
                            + "services) that they cannot reach directly.",
                    "Validate the destination against an allow-list of hosts/schemes and reject private, "
                            + "loopback and link-local address ranges before issuing the request.",
                    ANY, null),

            // ─── Transport & crypto ─────────────────────────────────────────────────
            rule("SEC-020", "TLS_VERIFICATION_DISABLED", Severity.HIGH,
                    "(?i)verify\\s*=\\s*False|rejectUnauthorized\\s*:\\s*false|"
                            + "NODE_TLS_REJECT_UNAUTHORIZED\\s*=\\s*['\"]?0|"
                            + "TrustAllCerts|ALLOW_ALL_HOSTNAME_VERIFIER|checkServerTrusted\\s*\\([^)]*\\)\\s*\\{\\s*\\}",
                    "TLS certificate or hostname verification is switched off. The connection is still "
                            + "encrypted but no longer authenticated, so it can be transparently intercepted.",
                    "Re-enable verification. For a self-signed internal certificate, add that specific "
                            + "certificate to the client's trust store rather than disabling validation globally.",
                    ANY, null),

            rule("SEC-021", "WEAK_HASH_ALGORITHM", Severity.MEDIUM,
                    "(?i)MessageDigest\\.getInstance\\s*\\(\\s*[\"'](?:MD5|SHA-?1)[\"']|"
                            + "hashlib\\.(?:md5|sha1)\\s*\\(|createHash\\s*\\(\\s*['\"](?:md5|sha1)['\"]",
                    "A cryptographically broken hash (MD5 or SHA-1) is in use. Both have practical collision "
                            + "attacks and neither is acceptable for signatures, integrity checks or password storage.",
                    "Use SHA-256 or better for integrity/digests. For passwords use a deliberately slow "
                            + "password hash — bcrypt, scrypt or Argon2 — never a general-purpose hash.",
                    ANY, null),

            // The RNG call is the match; the security keyword is a qualifier searched across the preceding
            // few lines. Requiring both on one line missed the common shape where a method named
            // generateToken() contains a bare `new Random()` on its own line.
            contextRule("SEC-022", "INSECURE_RANDOMNESS", Severity.MEDIUM,
                    "Math\\.random\\s*\\(|new\\s+Random\\s*\\(|random\\.random\\s*\\(|random\\.randint\\s*\\(",
                    "A security-sensitive value appears to be generated from a non-cryptographic pseudo-random "
                            + "generator. Such generators are deterministic given their seed, so an attacker who "
                            + "observes enough output can predict the values that follow.",
                    "Generate secrets with a cryptographically secure source: SecureRandom (Java), "
                            + "crypto.randomBytes (Node) or secrets.token_urlsafe (Python).",
                    ANY,
                    "(?i)\\b(?:token|secret|password|passwd|salt|nonce|otp|session|apikey|api[_-]?key|"
                            + "credential|reset|verify|auth|csrf)\\w*",
                    4),

            rule("SEC-023", "CLEARTEXT_TRANSPORT", Severity.LOW,
                    "[\"']http://(?!localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1])[a-zA-Z0-9.-]+",
                    "A plaintext http:// URL to a non-local host appears in source. Traffic to it can be read "
                            + "and modified in transit.",
                    "Use https:// for all external endpoints. Where a dependency only offers plaintext, "
                            + "document the exception explicitly.",
                    ANY, Pattern.compile("(?i)(xmlns|schemaLocation|w3\\.org|example\\.com|localhost|"
                            + "apache\\.org|maven\\.apache|springframework\\.org|\\.dtd|\\.xsd)")),

            // ─── Access control & framework configuration ───────────────────────────
            rule("SEC-030", "PERMISSIVE_CORS", Severity.MEDIUM,
                    "(?i)allowedOrigins?\\s*\\(\\s*[\"']\\*[\"']|setAllowedOriginPatterns?\\s*\\([^)]*[\"']\\*[\"']|"
                            + "Access-Control-Allow-Origin[\"']?\\s*[,:]\\s*[\"']\\*[\"']|"
                            + "cors\\s*\\(\\s*\\{[^}]*origin\\s*:\\s*[\"']\\*[\"']",
                    "CORS is configured to accept any origin. Combined with credentialed requests this lets "
                            + "any website drive the API using a logged-in user's browser session.",
                    "Replace the wildcard with an explicit list of trusted origins, supplied by configuration "
                            + "per environment.",
                    ANY, null),

            rule("SEC-031", "BROAD_UNAUTHENTICATED_ACCESS", Severity.MEDIUM,
                    "permitAll\\(\\)\\s*\\)?\\s*;?\\s*$|anyRequest\\(\\)\\s*\\.permitAll\\(\\)",
                    "A route matcher grants unauthenticated access. If the matcher is broader than intended "
                            + "(or is anyRequest()), protected functionality is exposed.",
                    "Default to authenticated() for anyRequest() and open up only the specific paths that must "
                            + "be public (login, registration, health, docs).",
                    JVM, null),

            rule("SEC-032", "CSRF_PROTECTION_DISABLED", Severity.MEDIUM,
                    "csrf\\s*\\([^)]*disable\\(\\)|csrf\\s*:\\s*false|CSRF_ENABLED\\s*=\\s*False",
                    "CSRF protection is disabled. This is a legitimate choice for a purely token-authenticated "
                            + "API (no ambient cookie credentials), and a serious hole for anything cookie-based — "
                            + "confirm which applies here.",
                    "If any endpoint authenticates via cookies or sessions, re-enable CSRF protection. If the "
                            + "API is strictly Bearer-token based, document that reasoning next to the setting.",
                    ANY, null),

            rule("SEC-033", "DEBUG_MODE_ENABLED", Severity.MEDIUM,
                    "(?i)debug\\s*=\\s*True|app\\.run\\s*\\([^)]*debug\\s*=\\s*True|"
                            + "DEBUG\\s*[:=]\\s*true|devtools\\.restart\\.enabled\\s*=\\s*true",
                    "Debug mode is enabled. Debug modes typically expose stack traces, configuration and — in "
                            + "some frameworks — an interactive console to anyone who can trigger an error.",
                    "Drive the debug flag from environment configuration and ensure it is false wherever the "
                            + "application is reachable by untrusted users.",
                    ANY, null),

            // ─── Information disclosure & error handling ────────────────────────────
            rule("SEC-040", "SENSITIVE_DATA_IN_LOGS", Severity.MEDIUM,
                    "(?i)(?:log(?:ger)?|console)\\s*\\.\\s*(?:info|debug|log|warn|error|trace)\\s*\\("
                            + "[^)]*\\b(?:password|passwd|secret|api[_-]?key|token|credit[_-]?card|ssn)\\b",
                    "A credential or other sensitive value is written to the application log. Logs are widely "
                            + "replicated to aggregation systems and read by people who should not see secrets.",
                    "Log a non-reversible identifier instead of the value itself, or redact the field before "
                            + "logging.",
                    ANY, null),

            rule("SEC-041", "STACK_TRACE_DISCLOSURE", Severity.LOW,
                    "printStackTrace\\s*\\(\\s*\\)|traceback\\.print_exc\\s*\\(",
                    "An exception stack trace is printed directly rather than logged through the application's "
                            + "logger. This bypasses log configuration and can leak internal structure.",
                    "Log the exception via the logger at an appropriate level so it is captured, correlated and "
                            + "kept out of user-facing responses.",
                    ANY, null),

            rule("SEC-042", "SWALLOWED_EXCEPTION", Severity.LOW,
                    "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}|except[^:]*:\\s*pass\\s*$",
                    "An exception is caught and discarded with no handling or logging. Failures here are "
                            + "invisible: the system continues in an unknown state and the cause is unrecoverable "
                            + "during an incident.",
                    "At minimum log the exception. If it is genuinely expected and benign, add a comment "
                            + "stating why so the silence is intentional and reviewable.",
                    ANY, null)
    );

    private static SecurityRule rule(String id, String category, Severity severity, String regex,
                                     String description, String recommendation,
                                     Set<String> extensions, Pattern falsePositiveFilter) {
        return new SecurityRule(id, category, severity, Pattern.compile(regex), description, recommendation,
                extensions, falsePositiveFilter, null, 0);
    }

    /**
     * A rule that fires only when a qualifying pattern also appears within {@code contextLines} lines above
     * the match. Used where the construct is innocuous on its own and only meaningful in context — see
     * SEC-022, where {@code new Random()} matters because of the surrounding method, not by itself.
     */
    private static SecurityRule contextRule(String id, String category, Severity severity, String regex,
                                            String description, String recommendation,
                                            Set<String> extensions, String contextRegex, int contextLines) {
        return new SecurityRule(id, category, severity, Pattern.compile(regex), description, recommendation,
                extensions, null, Pattern.compile(contextRegex), contextLines);
    }
}
