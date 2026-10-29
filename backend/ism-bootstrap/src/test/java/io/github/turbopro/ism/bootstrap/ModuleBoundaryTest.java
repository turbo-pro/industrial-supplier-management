package io.github.turbopro.ism.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

/** Source/descriptor gate: runs in the normal Maven unit-test lifecycle, without a database. */
class ModuleBoundaryTest {
    private static final String PREFIX = "io.github.turbopro.ism.";
    private static final String TENANT_MAPPER_CONTRACT = PREFIX + "common.infrastructure.tenant.TenantScopedMapper";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    @TempDir Path fixture;

    @Test void registeredReactorHasNoCyclesMissingModulesOrInfrastructureReverseDependencies() throws Exception {
        Path root = repository();
        var backend = root.resolve("backend");
        var registered = modules(backend.resolve("pom.xml"));
        var actual = new TreeSet<String>();
        try (var dirs = Files.list(backend)) {
            dirs.filter(Files::isDirectory).filter(p -> p.getFileName().toString().startsWith("ism-"))
                    .filter(p -> Files.isRegularFile(p.resolve("pom.xml")))
                    .forEach(p -> actual.add(p.getFileName().toString()));
        }
        assertThat(registered).as("backend reactor registration").containsExactlyInAnyOrderElementsOf(actual);
        assertThat(modules(root.resolve("pom.xml"))).containsExactly("backend");
        Map<String, Set<String>> graph = new TreeMap<>();
        for (String name : registered) {
            Element project = xml(backend.resolve(name).resolve("pom.xml"));
            assertThat(text(project, "artifactId")).as(name + " artifactId").isEqualTo(name);
            Set<String> edges = new TreeSet<>();
            for (Element dependency : children(child(project, "dependencies"), "dependency")) {
                String artifact = text(dependency, "artifactId");
                if (artifact.startsWith("ism-")) {
                    assertThat(text(dependency, "scope")).as(name + " internal dependency scope").isNotEqualTo("test");
                    edges.add(artifact);
                }
            }
            graph.put(name, edges);
        }
        assertThat(graphViolations(graph)).as("module dependency gate").isEmpty();
        assertThat(mapperViolations(backend)).as("production code must use public module contracts").isEmpty();
    }

    @Test void onlyBootstrapDeclaresAnApplicationAndAllFourFrontendApplicationsAreRegistered() throws Exception {
        Path root = repository();
        var applications = new ArrayList<Path>();
        for (Path source : sources(root.resolve("backend"))) {
            if (Files.readString(source).contains("@SpringBootApplication")) applications.add(source);
        }
        assertThat(applications).singleElement().satisfies(path -> assertThat(path.toString()).contains("ism-bootstrap/"));
        for (String app : List.of("console", "admin", "portal", "mobile")) {
            Path directory = root.resolve("frontend/apps/" + app);
            assertThat(directory.resolve("package.json")).isRegularFile();
            assertThat(Files.readString(directory.resolve("package.json"))).contains("@ism/" + app);
        }
        assertThat(root.resolve("mvnw")).isRegularFile();
        assertThat(root.resolve(".mvn/wrapper/maven-wrapper.properties")).isRegularFile();
        assertThat(root.resolve("pnpm-lock.yaml")).isRegularFile();
        // Directory registration is not evidence that the mobile application is implemented.
    }

    @Test void reverseDependenciesCyclesAndUnknownTargetsFailTheGate() {
        assertThat(graphViolations(Map.of("ism-common-api", Set.of("ism-supplier"), "ism-supplier", Set.of())))
                .anyMatch(message -> message.contains("common-api"));
        assertThat(graphViolations(Map.of("ism-common-infrastructure", Set.of("ism-iam"), "ism-iam", Set.of())))
                .anyMatch(message -> message.contains("common-infrastructure"));
        assertThat(graphViolations(Map.of("ism-iam", Set.of("ism-bootstrap"), "ism-bootstrap", Set.of())))
                .anyMatch(message -> message.contains("bootstrap"));
        assertThat(graphViolations(Map.of("ism-a", Set.of("ism-b"), "ism-b", Set.of("ism-a"))))
                .anyMatch(message -> message.contains("cycle"));
        assertThat(graphViolations(Map.of("ism-a", Set.of("ism-missing"))))
                .anyMatch(message -> message.contains("unregistered"));
    }

    @Test void mapperReferencesViaExplicitImportsFullyQualifiedTypesAndWildcardsAreRejected() throws Exception {
        javaFile("ism-iam", "iam.persistence", "AccountMapper", "@org.apache.ibatis.annotations.Mapper public interface AccountMapper {}");
        javaFile("ism-iam", "iam.persistence", "HiddenStore", "@org.apache.ibatis.annotations.Mapper public interface HiddenStore {}");
        javaFile("ism-supplier", "supplier", "Direct", "import " + PREFIX + "iam.persistence.AccountMapper; class Direct { AccountMapper mapper; }");
        javaFile("ism-supplier", "supplier", "Qualified", "class Qualified { " + PREFIX + "iam.persistence.AccountMapper mapper; }");
        javaFile("ism-supplier", "supplier", "Wildcard", "import " + PREFIX + "iam.persistence.*; class Wildcard { AccountMapper mapper; }");
        javaFile("ism-supplier", "supplier", "Annotated", "import " + PREFIX + "iam.persistence.HiddenStore; class Annotated { HiddenStore mapper; }");
        assertThat(mapperViolations(fixture)).hasSize(4);
    }

    @Test void ownedMappersAndTheTenantScopeMarkerAndPublicInterfacesRemainAllowed() throws Exception {
        javaFile("ism-iam", "iam", "AccountMapper", "public interface AccountMapper {}");
        javaFile("ism-common-infrastructure", "common.infrastructure.tenant", "TenantScopedMapper", "public interface TenantScopedMapper {}");
        javaFile("ism-iam", "iam", "PublicReader", "public interface PublicReader {}");
        javaFile("ism-iam", "iam", "OwnService", "import " + PREFIX + "iam.AccountMapper; class OwnService { AccountMapper mapper; }");
        javaFile("ism-supplier", "supplier", "Reader", "import " + PREFIX + "iam.PublicReader; import " + TENANT_MAPPER_CONTRACT + "; class Reader { PublicReader reader; }");
        assertThat(mapperViolations(fixture)).isEmpty();
    }

    static List<String> graphViolations(Map<String, Set<String>> graph) {
        var errors = new ArrayList<String>();
        graph.forEach((module, dependencies) -> dependencies.forEach(dependency -> {
            if (!graph.containsKey(dependency)) errors.add(module + " -> unregistered " + dependency);
            if (dependency.equals("ism-bootstrap")) errors.add(module + " must not depend on bootstrap");
            if (module.equals("ism-common-api")) errors.add("common-api cannot depend on " + dependency);
            if (module.equals("ism-common-infrastructure") && !dependency.equals("ism-common-api"))
                errors.add("common-infrastructure cannot depend on " + dependency);
        }));
        var complete = new HashSet<String>();
        for (String module : graph.keySet()) visit(module, graph, new LinkedHashSet<>(), complete, errors);
        return errors;
    }

    private static void visit(String module, Map<String, Set<String>> graph, Set<String> visiting,
                              Set<String> complete, List<String> errors) {
        if (complete.contains(module) || !graph.containsKey(module)) return;
        if (!visiting.add(module)) { errors.add("dependency cycle: " + visiting + " -> " + module); return; }
        for (String dependency : graph.get(module)) visit(dependency, graph, visiting, complete, errors);
        visiting.remove(module);
        complete.add(module);
    }

    private record Source(Path path, String module, String namespace, String text) {}
    private record Store(String type, String namespace, String name, String module) {}

    static List<String> mapperViolations(Path backend) throws Exception {
        List<Source> files = new ArrayList<>();
        List<Store> stores = new ArrayList<>();
        for (Path path : sources(backend)) {
            String text = Files.readString(path);
            var declaration = PACKAGE.matcher(text);
            if (!declaration.find()) throw new IllegalArgumentException("Missing package: " + path);
            String namespace = declaration.group(1);
            String module = backend.relativize(path).getName(0).toString();
            String name = path.getFileName().toString().replace(".java", "");
            files.add(new Source(path, module, namespace, text));
            if (name.endsWith("Mapper") || Pattern.compile("@(?:org\\.apache\\.ibatis\\.annotations\\.)?Mapper\\b").matcher(text).find())
                stores.add(new Store(namespace + "." + name, namespace, name, module));
        }
        var errors = new ArrayList<String>();
        for (Source source : files) for (Store store : stores) {
            if (source.module().equals(store.module()) || store.type().equals(TENANT_MAPPER_CONTRACT)) continue;
            boolean explicit = Pattern.compile("\\b" + Pattern.quote(store.type()) + "\\b").matcher(source.text()).find();
            boolean wildcard = Pattern.compile("import\\s+" + Pattern.quote(store.namespace()) + "\\.\\*\\s*;").matcher(source.text()).find()
                    && Pattern.compile("\\b" + Pattern.quote(store.name()) + "\\b").matcher(source.text()).find();
            if (explicit || wildcard) errors.add(source.module() + "/" + source.path().getFileName() + " references " + store.type());
        }
        return errors;
    }

    private static List<Path> sources(Path backend) throws Exception {
        try (var paths = Files.walk(backend)) {
            return paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .sorted().collect(Collectors.toList());
        }
    }

    private void javaFile(String module, String namespace, String name, String body) throws Exception {
        Path file = fixture.resolve(module + "/src/main/java/" + namespace.replace('.', '/') + "/" + name + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package " + PREFIX + namespace + ";\n" + body);
    }

    static Path repository() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !Files.isRegularFile(path.resolve("backend/pom.xml"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Cannot locate repository root");
        return path;
    }

    private static Element xml(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
    }
    private static List<Element> children(Element element, String name) {
        List<Element> result = new ArrayList<>();
        if (element != null) for (var node = element.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element e && e.getTagName().equals(name)) result.add(e);
        return result;
    }
    private static Element child(Element element, String name) { return children(element, name).stream().findFirst().orElse(null); }
    private static String text(Element element, String name) { Element value = child(element, name); return value == null ? "" : value.getTextContent().trim(); }
    private static List<String> modules(Path pom) throws Exception {
        return children(child(xml(pom), "modules"), "module").stream().map(Element::getTextContent).map(String::trim).toList();
    }
}
