package com.acme.analyzer.extractor;

import com.acme.analyzer.parser.AnalysisRequest;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.model.analysis.AnalysisSnapshot;
import com.acme.model.analysis.SourceFileRecord;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class JavaAnalysisFacade {
    private static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "boolean",
            "byte",
            "char",
            "double",
            "float",
            "int",
            "long",
            "short",
            "void"
    );

    public AnalysisSnapshot analyze(ProjectDescriptor descriptor, AnalysisRequest request) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(request, "request must not be null");

        List<Path> javaFiles = collectJavaFiles(descriptor, request);
        List<SourceFileRecord> files = new ArrayList<>();
        List<SymbolRecord> symbols = new ArrayList<>();
        List<PendingTypeRelation> pendingTypeRelations = new ArrayList<>();
        List<PendingMethodCall> pendingMethodCalls = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            analyzeFile(descriptor, javaFile, files, symbols, pendingTypeRelations, pendingMethodCalls);
        }

        Map<String, String> typeKeyByQualifiedName = new HashMap<>();
        Map<String, String> typeQualifiedNameBySymbolKey = new HashMap<>();
        for (SymbolRecord symbol : symbols) {
            if (symbol.symbolType() == SymbolType.TYPE) {
                typeKeyByQualifiedName.put(symbol.qualifiedName(), symbol.symbolKey());
                typeQualifiedNameBySymbolKey.put(symbol.symbolKey(), symbol.qualifiedName());
            }
        }
        Map<String, String> methodKeyByNameAndArity = new HashMap<>();
        for (SymbolRecord symbol : symbols) {
            if (symbol.symbolType() == SymbolType.METHOD) {
                String ownerQualifiedTypeName = typeQualifiedNameBySymbolKey.get(symbol.parentSymbolKey());
                if (ownerQualifiedTypeName != null) {
                    methodKeyByNameAndArity.put(
                            methodIndexKey(ownerQualifiedTypeName, symbol.name(), methodArity(symbol.signature())),
                            symbol.symbolKey()
                    );
                }
            }
        }

        List<RelationRecord> relations = new ArrayList<>();
        for (PendingTypeRelation relation : pendingTypeRelations) {
            relations.add(new RelationRecord(
                    relation.sourceSymbolKey(),
                    resolveTypeTargetKey(relation.targetQualifiedName(), typeKeyByQualifiedName),
                    relation.relationType(),
                    relation.confidence(),
                    relation.filePath(),
                    relation.sourceLine()
            ));
        }
        for (PendingMethodCall methodCall : pendingMethodCalls) {
            String targetMethodKey = methodKeyByNameAndArity.get(
                    methodIndexKey(methodCall.targetTypeQualifiedName(), methodCall.targetMethodName(), methodCall.argumentCount())
            );
            if (targetMethodKey != null) {
                relations.add(new RelationRecord(
                        methodCall.sourceMethodSymbolKey(),
                        targetMethodKey,
                        RelationType.CALLS,
                        methodCall.confidence(),
                        methodCall.filePath(),
                        methodCall.sourceLine()
                ));
            }
        }
        relations = deduplicateRelations(relations);

        String note = request.incremental()
                ? "Incremental request rebuilt " + javaFiles.size() + " Java file(s)."
                : "Full project scan completed with local AST extraction.";

        return new AnalysisSnapshot(
                request.snapshotId(),
                descriptor.projectId(),
                Instant.now(),
                files,
                symbols,
                relations,
                note
        );
    }

    private void analyzeFile(
            ProjectDescriptor descriptor,
            Path javaFile,
            List<SourceFileRecord> files,
            List<SymbolRecord> symbols,
            List<PendingTypeRelation> pendingTypeRelations,
            List<PendingMethodCall> pendingMethodCalls
    ) {
        String content = readFile(javaFile);
        CompilationUnit compilationUnit = parseCompilationUnit(descriptor, javaFile, content);
        String relativeFilePath = toRelativePath(descriptor.rootPath(), javaFile);
        String moduleName = resolveModuleName(descriptor, javaFile);
        FileAnalysisVisitor visitor = new FileAnalysisVisitor(
                relativeFilePath,
                moduleName,
                compilationUnit,
                symbols,
                pendingTypeRelations,
                pendingMethodCalls
        );
        compilationUnit.accept(visitor);

        files.add(new SourceFileRecord(
                relativeFilePath,
                moduleName,
                visitor.packageName(),
                sha256(content),
                resolveScope(relativeFilePath)
        ));
    }

    private List<Path> collectJavaFiles(ProjectDescriptor descriptor, AnalysisRequest request) {
        if (request.incremental() && request.changedFiles() != null && !request.changedFiles().isEmpty()) {
            return resolveRequestedJavaFiles(descriptor, request.changedFiles());
        }

        Set<Path> javaFiles = new TreeSet<>(Comparator.comparing(Path::toString));
        List<Path> roots = descriptor.sourceRoots().isEmpty() ? List.of(descriptor.rootPath()) : descriptor.sourceRoots();
        for (Path sourceRoot : roots) {
            if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to scan Java files under: " + sourceRoot, exception);
            }
        }
        return List.copyOf(javaFiles);
    }

    private List<Path> resolveRequestedJavaFiles(ProjectDescriptor descriptor, List<String> changedFiles) {
        Set<Path> javaFiles = new TreeSet<>(Comparator.comparing(Path::toString));
        for (String changedFile : changedFiles) {
            if (changedFile == null || changedFile.isBlank()) {
                continue;
            }

            Path resolvedPath = descriptor.rootPath().resolve(changedFile).normalize();
            if (!resolvedPath.startsWith(descriptor.rootPath())) {
                continue;
            }
            if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
                continue;
            }
            if (!resolvedPath.getFileName().toString().endsWith(".java")) {
                continue;
            }
            javaFiles.add(resolvedPath);
        }
        return List.copyOf(javaFiles);
    }

    private CompilationUnit parseCompilationUnit(ProjectDescriptor descriptor, Path javaFile, String content) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(content.toCharArray());
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        configureParserEnvironment(parser, descriptor, javaFile);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }

    private void configureParserEnvironment(ASTParser parser, ProjectDescriptor descriptor, Path javaFile) {
        List<Path> sourceRoots = descriptor.sourceRoots().isEmpty() ? List.of(descriptor.rootPath()) : descriptor.sourceRoots();
        String[] sourceEntries = sourceRoots.stream()
                .map(Path::toString)
                .toArray(String[]::new);
        String[] encodings = sourceRoots.stream()
                .map(ignored -> StandardCharsets.UTF_8.name())
                .toArray(String[]::new);
        parser.setEnvironment(new String[0], sourceEntries, encodings, true);
        parser.setUnitName(resolveUnitName(descriptor, javaFile));
    }

    private String resolveUnitName(ProjectDescriptor descriptor, Path javaFile) {
        Optional<Path> matchedSourceRoot = descriptor.sourceRoots().stream()
                .filter(javaFile::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount));
        Path unitPath = matchedSourceRoot.isPresent()
                ? matchedSourceRoot.get().relativize(javaFile)
                : descriptor.rootPath().relativize(javaFile);
        return unitPath.toString().replace('\\', '/');
    }

    private String readFile(Path javaFile) {
        try {
            return Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Java file: " + javaFile, exception);
        }
    }

    private String toRelativePath(Path rootPath, Path filePath) {
        return rootPath.relativize(filePath).toString().replace('\\', '/');
    }

    private String resolveModuleName(ProjectDescriptor descriptor, Path filePath) {
        Optional<Path> matchedModuleRoot = descriptor.moduleRoots().stream()
                .filter(filePath::startsWith)
                .max(Comparator.comparingInt(path -> path.getNameCount()));
        if (matchedModuleRoot.isEmpty()) {
            return "root";
        }
        Path relativeModulePath = descriptor.rootPath().relativize(matchedModuleRoot.get());
        if (relativeModulePath.toString().isBlank()) {
            return "root";
        }
        return relativeModulePath.toString()
                .replace('\\', '/')
                .replace('/', '.');
    }

    private String resolveScope(String relativeFilePath) {
        return relativeFilePath.contains("/src/test/java/") ? "test" : "main";
    }

    private String resolveTypeTargetKey(String targetQualifiedName, Map<String, String> typeKeyByQualifiedName) {
        return typeKeyByQualifiedName.getOrDefault(targetQualifiedName, "external:type:" + targetQualifiedName);
    }

    private int methodArity(String signature) {
        int start = signature.indexOf('(');
        int end = signature.indexOf(')');
        if (start < 0 || end < 0 || end <= start + 1) {
            return 0;
        }
        return (int) signature.substring(start + 1, end).chars().filter(ch -> ch == ',').count() + 1;
    }

    private String methodIndexKey(String parentQualifiedTypeName, String methodName, int arity) {
        return parentQualifiedTypeName + "|" + methodName + "|" + arity;
    }

    private List<RelationRecord> deduplicateRelations(List<RelationRecord> relations) {
        Map<String, RelationRecord> uniqueRelations = new LinkedHashMap<>();
        for (RelationRecord relation : relations) {
            String relationKey = relation.sourceSymbolKey() + "|" + relation.targetSymbolKey() + "|" + relation.relationType().name();
            uniqueRelations.putIfAbsent(relationKey, relation);
        }
        return List.copyOf(uniqueRelations.values());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private final class FileAnalysisVisitor extends ASTVisitor {

        private final String relativeFilePath;
        private final String moduleName;
        private final CompilationUnit compilationUnit;
        private final List<SymbolRecord> symbols;
        private final List<PendingTypeRelation> pendingTypeRelations;
        private final List<PendingMethodCall> pendingMethodCalls;
        private final Deque<TypeContext> typeStack = new ArrayDeque<>();
        private final Deque<MethodContext> methodStack = new ArrayDeque<>();
        private final Map<String, String> exactImports = new LinkedHashMap<>();
        private final LinkedHashSet<String> wildcardImports = new LinkedHashSet<>();
        private String packageName = "";

        private FileAnalysisVisitor(
                String relativeFilePath,
                String moduleName,
                CompilationUnit compilationUnit,
                List<SymbolRecord> symbols,
                List<PendingTypeRelation> pendingTypeRelations,
                List<PendingMethodCall> pendingMethodCalls
        ) {
            this.relativeFilePath = relativeFilePath;
            this.moduleName = moduleName;
            this.compilationUnit = compilationUnit;
            this.symbols = symbols;
            this.pendingTypeRelations = pendingTypeRelations;
            this.pendingMethodCalls = pendingMethodCalls;
            collectImports();
        }

        @Override
        public boolean visit(PackageDeclaration node) {
            this.packageName = node.getName().getFullyQualifiedName();
            return super.visit(node);
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            TypeContext context = createTypeContext(
                    node.getName().getIdentifier(),
                    node.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS,
                    buildTypeSignature(node.getName().getIdentifier(), node.getSuperclassType(), node.superInterfaceTypes()),
                    node.toString(),
                    node.getStartPosition(),
                    node.getLength()
            );
            typeStack.push(context);
            addExtendsRelation(context.symbolKey(), node.getSuperclassType(), node.getStartPosition());
            addImplementsRelations(context.symbolKey(), node.superInterfaceTypes(), node.getStartPosition());
            return super.visit(node);
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            typeStack.pop();
            super.endVisit(node);
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            TypeContext context = createTypeContext(
                    node.getName().getIdentifier(),
                    SymbolKind.ENUM,
                    buildTypeSignature(node.getName().getIdentifier(), null, node.superInterfaceTypes()),
                    node.toString(),
                    node.getStartPosition(),
                    node.getLength()
            );
            typeStack.push(context);
            addImplementsRelations(context.symbolKey(), node.superInterfaceTypes(), node.getStartPosition());
            return super.visit(node);
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            typeStack.pop();
            super.endVisit(node);
        }

        @Override
        public boolean visit(RecordDeclaration node) {
            TypeContext context = createTypeContext(
                    node.getName().getIdentifier(),
                    SymbolKind.RECORD,
                    node.getName().getIdentifier() + node.recordComponents(),
                    node.toString(),
                    node.getStartPosition(),
                    node.getLength()
            );
            typeStack.push(context);
            addImplementsRelations(context.symbolKey(), node.superInterfaceTypes(), node.getStartPosition());
            @SuppressWarnings("unchecked")
            List<SingleVariableDeclaration> recordComponents = node.recordComponents();
            for (SingleVariableDeclaration component : recordComponents) {
                registerVisibleType(context.fieldTypes(), component.getName().getIdentifier(), component.getType());
                addUsesTypeRelations(context, component.getType(), component.getStartPosition());
            }
            return super.visit(node);
        }

        @Override
        public void endVisit(RecordDeclaration node) {
            typeStack.pop();
            super.endVisit(node);
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            typeStack.push(createTypeContext(
                    node.getName().getIdentifier(),
                    SymbolKind.ANNOTATION,
                    node.getName().getIdentifier(),
                    node.toString(),
                    node.getStartPosition(),
                    node.getLength()
            ));
            return super.visit(node);
        }

        @Override
        public void endVisit(AnnotationTypeDeclaration node) {
            typeStack.pop();
            super.endVisit(node);
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (typeStack.isEmpty()) {
                return super.visit(node);
            }
            TypeContext typeContext = typeStack.peek();
            String methodName = node.isConstructor() ? "<init>" : node.getName().getIdentifier();
            String parameterList = buildParameterList(node);
            String signature = typeContext.qualifiedName() + "#" + methodName + "(" + parameterList + ")";
            String symbolKey = "method:" + moduleName + ":" + signature;
            String implText = Optional.ofNullable(node.getBody()).map(Object::toString).orElse("");
            symbols.add(new SymbolRecord(
                    symbolKey,
                    SymbolType.METHOD,
                    node.isConstructor() ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD,
                    typeContext.symbolKey(),
                    methodName,
                    packageName,
                    typeContext.qualifiedName() + "#" + methodName,
                    methodName,
                    signature,
                    relativeFilePath,
                    compilationUnit.getLineNumber(node.getStartPosition()),
                    compilationUnit.getLineNumber(node.getStartPosition() + node.getLength()),
                    sha256(signature),
                    sha256(signature + "\n" + implText),
                    ChangeStatus.UNCHANGED
            ));
            pendingTypeRelations.add(new PendingTypeRelation(
                    typeContext.symbolKey(),
                    symbolKey,
                    RelationType.DECLARES,
                    "exact",
                    relativeFilePath,
                    compilationUnit.getLineNumber(node.getStartPosition())
            ));
            addUsesTypeRelations(typeContext, node.getReturnType2(), node.getStartPosition());
            @SuppressWarnings("unchecked")
            List<SingleVariableDeclaration> parameters = node.parameters();
            Map<String, String> visibleTypesByName = new HashMap<>();
            for (SingleVariableDeclaration parameter : parameters) {
                registerVisibleType(visibleTypesByName, parameter.getName().getIdentifier(), parameter.getType());
                addUsesTypeRelations(typeContext, parameter.getType(), parameter.getStartPosition());
            }
            @SuppressWarnings("unchecked")
            List<Type> thrownExceptionTypes = node.thrownExceptionTypes();
            for (Type exceptionType : thrownExceptionTypes) {
                addUsesTypeRelations(typeContext, exceptionType, node.getStartPosition());
            }
            methodStack.push(new MethodContext(
                    symbolKey,
                    typeContext.symbolKey(),
                    typeContext.qualifiedName(),
                    visibleTypesByName
            ));
            return super.visit(node);
        }

        @Override
        public void endVisit(MethodDeclaration node) {
            if (!methodStack.isEmpty()) {
                methodStack.pop();
            }
            super.endVisit(node);
        }

        @Override
        public boolean visit(MethodInvocation node) {
            if (!methodStack.isEmpty()) {
                MethodContext methodContext = methodStack.peek();
                PendingMethodCall pendingMethodCall = resolveMethodInvocation(node, methodContext);
                if (pendingMethodCall != null) {
                    pendingMethodCalls.add(pendingMethodCall);
                }
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            if (!methodStack.isEmpty()) {
                MethodContext methodContext = methodStack.peek();
                PendingMethodCall pendingMethodCall = resolveBindingBackedMethodCall(
                        node.resolveMethodBinding(),
                        methodContext,
                        node.getName().getIdentifier(),
                        node.arguments().size(),
                        node.getStartPosition()
                );
                if (pendingMethodCall != null) {
                    pendingMethodCalls.add(pendingMethodCall);
                }
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(FieldDeclaration node) {
            if (!typeStack.isEmpty()) {
                TypeContext typeContext = typeStack.peek();
                registerVisibleTypes(typeContext.fieldTypes(), node.getType(), node.fragments());
                addUsesTypeRelations(typeContext, node.getType(), node.getStartPosition());
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(VariableDeclarationStatement node) {
            if (!typeStack.isEmpty()) {
                addUsesTypeRelations(typeStack.peek(), node.getType(), node.getStartPosition());
            }
            if (!methodStack.isEmpty()) {
                registerVisibleTypes(methodStack.peek().visibleTypesByName(), node.getType(), node.fragments());
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(VariableDeclarationExpression node) {
            if (!typeStack.isEmpty()) {
                addUsesTypeRelations(typeStack.peek(), node.getType(), node.getStartPosition());
            }
            if (!methodStack.isEmpty()) {
                registerVisibleTypes(methodStack.peek().visibleTypesByName(), node.getType(), node.fragments());
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(ClassInstanceCreation node) {
            if (!typeStack.isEmpty()) {
                addUsesTypeRelations(typeStack.peek(), node.getType(), node.getStartPosition());
            }
            return super.visit(node);
        }

        public String packageName() {
            return packageName;
        }

        private void collectImports() {
            @SuppressWarnings("unchecked")
            List<ImportDeclaration> imports = compilationUnit.imports();
            for (ImportDeclaration importDeclaration : imports) {
                String fullyQualifiedName = importDeclaration.getName().getFullyQualifiedName();
                if (importDeclaration.isOnDemand()) {
                    wildcardImports.add(fullyQualifiedName);
                    continue;
                }
                String simpleName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
                exactImports.put(simpleName, fullyQualifiedName);
            }
        }

        private TypeContext createTypeContext(
                String simpleName,
                SymbolKind kind,
                String apiSignature,
                String implSignature,
                int startPosition,
                int length
        ) {
            String qualifiedName = buildQualifiedTypeName(simpleName);
            String symbolKey = "type:" + moduleName + ":" + qualifiedName;
            symbols.add(new SymbolRecord(
                    symbolKey,
                    SymbolType.TYPE,
                    kind,
                    null,
                    simpleName,
                    packageName,
                    qualifiedName,
                    simpleName,
                    qualifiedName,
                    relativeFilePath,
                    compilationUnit.getLineNumber(startPosition),
                    compilationUnit.getLineNumber(startPosition + length),
                    sha256(apiSignature),
                    sha256(implSignature),
                    ChangeStatus.UNCHANGED
            ));
            return new TypeContext(symbolKey, qualifiedName, new HashMap<>());
        }

        private void addExtendsRelation(String sourceTypeKey, Type superClassType, int position) {
            if (superClassType == null) {
                return;
            }
            pendingTypeRelations.add(new PendingTypeRelation(
                    sourceTypeKey,
                    resolveTypeName(superClassType),
                    RelationType.EXTENDS,
                    "possible",
                    relativeFilePath,
                    compilationUnit.getLineNumber(position)
            ));
        }

        @SuppressWarnings("unchecked")
        private void addImplementsRelations(String sourceTypeKey, List<Type> superInterfaceTypes, int position) {
            for (Type interfaceType : superInterfaceTypes) {
                pendingTypeRelations.add(new PendingTypeRelation(
                        sourceTypeKey,
                        resolveTypeName(interfaceType),
                        RelationType.IMPLEMENTS,
                        "possible",
                        relativeFilePath,
                        compilationUnit.getLineNumber(position)
                ));
            }
        }

        private void addUsesTypeRelations(TypeContext sourceType, Type referencedType, int position) {
            if (referencedType == null) {
                return;
            }
            for (String rawTypeName : collectReferencedTypeNames(referencedType)) {
                String resolvedTypeName = resolveTypeName(rawTypeName);
                if (resolvedTypeName == null || resolvedTypeName.equals(sourceType.qualifiedName())) {
                    continue;
                }
                pendingTypeRelations.add(new PendingTypeRelation(
                        sourceType.symbolKey(),
                        resolvedTypeName,
                        RelationType.USES_TYPE,
                        "possible",
                        relativeFilePath,
                        compilationUnit.getLineNumber(position)
                ));
            }
        }

        @SuppressWarnings("unchecked")
        private String buildParameterList(MethodDeclaration node) {
            List<SingleVariableDeclaration> parameters = node.parameters();
            List<String> parameterTypes = new ArrayList<>(parameters.size());
            for (SingleVariableDeclaration parameter : parameters) {
                parameterTypes.add(stripGenericSuffix(parameter.getType().toString()));
            }
            return String.join(", ", parameterTypes);
        }

        private String buildQualifiedTypeName(String simpleName) {
            if (typeStack.isEmpty()) {
                return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
            }
            return typeStack.peek().qualifiedName() + "." + simpleName;
        }

        private String buildTypeSignature(String simpleName, Type superClassType, List<?> superInterfaces) {
            StringBuilder builder = new StringBuilder(simpleName);
            if (superClassType != null) {
                builder.append(" extends ").append(superClassType);
            }
            if (!superInterfaces.isEmpty()) {
                builder.append(" implements ").append(superInterfaces);
            }
            return builder.toString();
        }

        private String resolveTypeName(Type type) {
            if (type == null || type.isPrimitiveType()) {
                return null;
            }
            return resolveTypeName(type.toString());
        }

        private String resolveTypeName(String rawTypeName) {
            String normalizedTypeName = stripGenericSuffix(rawTypeName);
            if (normalizedTypeName.isBlank()
                    || normalizedTypeName.equals("var")
                    || PRIMITIVE_TYPE_NAMES.contains(normalizedTypeName)) {
                return null;
            }
            if (normalizedTypeName.contains(".")) {
                String firstSegment = normalizedTypeName.substring(0, normalizedTypeName.indexOf('.'));
                if (exactImports.containsKey(firstSegment)) {
                    return exactImports.get(firstSegment) + normalizedTypeName.substring(normalizedTypeName.indexOf('.'));
                }
                if (!packageName.isBlank() && Character.isUpperCase(firstSegment.charAt(0))) {
                    return packageName + "." + normalizedTypeName;
                }
                return normalizedTypeName;
            }
            if (exactImports.containsKey(normalizedTypeName)) {
                return exactImports.get(normalizedTypeName);
            }
            if (wildcardImports.isEmpty()) {
                return packageName.isBlank() ? normalizedTypeName : packageName + "." + normalizedTypeName;
            }
            return wildcardImports.iterator().next() + "." + normalizedTypeName;
        }

        private Set<String> collectReferencedTypeNames(Type type) {
            LinkedHashSet<String> typeNames = new LinkedHashSet<>();
            type.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleType node) {
                    typeNames.add(node.getName().getFullyQualifiedName());
                    return false;
                }

                @Override
                public boolean visit(QualifiedType node) {
                    typeNames.add(node.toString());
                    return false;
                }

                @Override
                public boolean visit(NameQualifiedType node) {
                    typeNames.add(node.toString());
                    return false;
                }
            });
            return typeNames;
        }

        private String stripGenericSuffix(String typeName) {
            String withoutGenerics = typeName.replaceAll("<.*>", "");
            return withoutGenerics.replace("[]", "").trim();
        }

        @SuppressWarnings("unchecked")
        private void registerVisibleTypes(Map<String, String> visibleTypesByName, Type declaredType, List<?> fragments) {
            for (Object fragment : fragments) {
                if (fragment instanceof VariableDeclarationFragment declarationFragment) {
                    registerVisibleType(
                            visibleTypesByName,
                            declarationFragment.getName().getIdentifier(),
                            declaredType
                    );
                }
            }
        }

        private void registerVisibleType(Map<String, String> visibleTypesByName, String identifier, Type declaredType) {
            String resolvedTypeName = resolveTypeName(declaredType);
            if (resolvedTypeName == null) {
                return;
            }
            visibleTypesByName.put(identifier, resolvedTypeName);
        }

        private PendingMethodCall resolveMethodInvocation(MethodInvocation node, MethodContext methodContext) {
            PendingMethodCall bindingBackedCall = resolveBindingBackedMethodCall(
                    node.resolveMethodBinding(),
                    methodContext,
                    node.getName().getIdentifier(),
                    node.arguments().size(),
                    node.getStartPosition()
            );
            if (bindingBackedCall != null) {
                return bindingBackedCall;
            }

            String fallbackTargetType = resolveFallbackMethodTargetType(node.getExpression(), methodContext);
            if (fallbackTargetType != null) {
                return new PendingMethodCall(
                        methodContext.symbolKey(),
                        fallbackTargetType,
                        node.getName().getIdentifier(),
                        node.arguments().size(),
                        "possible",
                        relativeFilePath,
                        compilationUnit.getLineNumber(node.getStartPosition())
                );
            }
            return null;
        }

        private String resolveFallbackMethodTargetType(Expression expression, MethodContext methodContext) {
            if (expression == null || expression instanceof ThisExpression) {
                return methodContext.parentTypeQualifiedName();
            }

            String bindingBackedType = normalizeQualifiedTypeName(expression.resolveTypeBinding());
            if (bindingBackedType != null && !bindingBackedType.isBlank()) {
                return bindingBackedType;
            }

            if (expression instanceof SimpleName simpleName) {
                return resolveSimpleNameTargetType(simpleName, methodContext);
            }
            if (expression instanceof FieldAccess fieldAccess) {
                if (fieldAccess.getExpression() instanceof ThisExpression) {
                    return currentFieldType(fieldAccess.getName().getIdentifier());
                }
                return null;
            }
            if (expression instanceof QualifiedName qualifiedName) {
                return resolveQualifiedNameTargetType(qualifiedName);
            }
            if (expression instanceof ClassInstanceCreation classInstanceCreation) {
                return resolveTypeName(classInstanceCreation.getType());
            }
            return null;
        }

        private String resolveSimpleNameTargetType(SimpleName simpleName, MethodContext methodContext) {
            String identifier = simpleName.getIdentifier();
            String visibleType = methodContext.visibleTypesByName().get(identifier);
            if (visibleType != null) {
                return visibleType;
            }

            String fieldType = currentFieldType(identifier);
            if (fieldType != null) {
                return fieldType;
            }

            return looksLikeTypeName(identifier) ? resolveTypeName(identifier) : null;
        }

        private String resolveQualifiedNameTargetType(QualifiedName qualifiedName) {
            String candidateTypeName = qualifiedName.getFullyQualifiedName();
            return looksLikeTypeName(candidateTypeName) ? resolveTypeName(candidateTypeName) : null;
        }

        private String currentFieldType(String identifier) {
            if (typeStack.isEmpty()) {
                return null;
            }
            return typeStack.peek().fieldTypes().get(identifier);
        }

        private boolean looksLikeTypeName(String candidate) {
            if (candidate == null || candidate.isBlank()) {
                return false;
            }
            int lastDot = candidate.lastIndexOf('.');
            String simpleName = lastDot >= 0 ? candidate.substring(lastDot + 1) : candidate;
            return !simpleName.isBlank() && Character.isUpperCase(simpleName.charAt(0));
        }

        private PendingMethodCall resolveBindingBackedMethodCall(
                IMethodBinding methodBinding,
                MethodContext methodContext,
                String fallbackMethodName,
                int fallbackArgumentCount,
                int sourcePosition
        ) {
            if (methodBinding == null) {
                return null;
            }

            IMethodBinding declarationBinding = methodBinding.getMethodDeclaration();
            ITypeBinding declaringClass = declarationBinding.getDeclaringClass();
            String targetTypeQualifiedName = normalizeQualifiedTypeName(declaringClass);
            if (targetTypeQualifiedName == null || targetTypeQualifiedName.isBlank()) {
                return null;
            }

            return new PendingMethodCall(
                    methodContext.symbolKey(),
                    targetTypeQualifiedName,
                    declarationBinding.getName() == null || declarationBinding.getName().isBlank()
                            ? fallbackMethodName
                            : declarationBinding.getName(),
                    declarationBinding.getParameterTypes() == null
                            ? fallbackArgumentCount
                            : declarationBinding.getParameterTypes().length,
                    "exact",
                    relativeFilePath,
                    compilationUnit.getLineNumber(sourcePosition)
            );
        }

        private String normalizeQualifiedTypeName(ITypeBinding typeBinding) {
            if (typeBinding == null) {
                return null;
            }

            ITypeBinding erasure = typeBinding.getErasure();
            String qualifiedName = erasure.getQualifiedName();
            if (qualifiedName == null || qualifiedName.isBlank()) {
                qualifiedName = erasure.getBinaryName();
            }
            if (qualifiedName == null || qualifiedName.isBlank()) {
                qualifiedName = erasure.getName();
            }
            if (qualifiedName == null || qualifiedName.isBlank()) {
                return null;
            }
            return qualifiedName.replace('$', '.');
        }
    }

    private record TypeContext(
            String symbolKey,
            String qualifiedName,
            Map<String, String> fieldTypes
    ) {
    }

    private record MethodContext(
            String symbolKey,
            String parentTypeSymbolKey,
            String parentTypeQualifiedName,
            Map<String, String> visibleTypesByName
    ) {
    }

    private record PendingTypeRelation(
            String sourceSymbolKey,
            String targetQualifiedName,
            RelationType relationType,
            String confidence,
            String filePath,
            Integer sourceLine
    ) {
    }

    private record PendingMethodCall(
            String sourceMethodSymbolKey,
            String targetTypeQualifiedName,
            String targetMethodName,
            int argumentCount,
            String confidence,
            String filePath,
            Integer sourceLine
    ) {
    }
}
