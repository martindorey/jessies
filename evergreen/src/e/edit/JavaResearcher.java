package e.edit;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import java.util.zip.*;
import e.ptextarea.*;
import e.util.*;

public class JavaResearcher implements WorkspaceResearcher {
    private static final String INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;";
    private static final String NEWLINE = "<br>" + INDENT;
    private static final String COMMA = ",&nbsp;";
    
    private static final Set<String> uniqueIdentifiers = new TreeSet<>();
    
    private static String[] javaDocSummary = new String[0];
    
    private static final JavaResearcher INSTANCE = new JavaResearcher();
    
    private JavaResearcher() {
        init();
    }
    
    public synchronized static JavaResearcher getSharedInstance() {
        return INSTANCE;
    }
    
    private static void init() {
        final long t0 = System.nanoTime();
        
        final String filename = Evergreen.getResourceFilename("lib", "data", "javadoc-summary.txt.gz");
        try {
            javaDocSummary = gunzipTextFile(filename).split("\n");
        } catch (IOException ex) {
            Log.warn("Failed to read JavaDoc summary from \"" + filename + "\"", ex);
        }
        Log.warn("Read JavaDoc summary from \"" + filename + "\" in " + TimeUtilities.nsToString(System.nanoTime() - t0));
        
        Log.warn("Scanning JavaDoc summary...");
        Pattern identifierPattern = Pattern.compile("^[MCFEA]:(\\S+?)(\\(|\t).*$");
        
        int classCount = 0;
        for (String line : javaDocSummary) {
            if (line.startsWith("Class:")) {
                classCount++;
                // Some classes don't have accessible constructors, so add
                // the class name anyway (SwingUtilities is an example).
                String className = line.substring(line.lastIndexOf('.') + 1);
                uniqueIdentifiers.add(className);
            } else {
                // Is it a constructor, method or field definition?
                Matcher matcher = identifierPattern.matcher(line);
                if (matcher.find()) {
                    uniqueIdentifiers.add(matcher.group(1));
                }
            }
        }
        
        // Prime the spelling checker with all the unique words we found.
        final Set<String> uniqueWords = new TreeSet<>();
        Advisor.extractUniqueWords(uniqueIdentifiers, uniqueWords);
        SpellingChecker.getSharedSpellingCheckerInstance().addSpellingExceptionsFor(FileType.JAVA, uniqueWords);
        
        final long t1 = System.nanoTime();
        Log.warn("Read summarized JavaDoc for " + classCount + " classes (" + javaDocSummary.length + " lines, " + uniqueIdentifiers.size() + " unique identifiers, " + uniqueWords.size() + " unique words) in " + TimeUtilities.nsToString(t1 - t0) + ".");
    }
    
    private static String gunzipTextFile(String filename) throws IOException {
        final Path file = FileUtilities.pathFrom(filename);
        if (!Files.exists(file)) {
            return "";
        }
        final InputStream in = new GZIPInputStream(new FileInputStream(file.toFile()));
        try {
            final byte[] buf = new byte[8192];
            final ByteArrayOutputStream uncompressedBytes = new ByteArrayOutputStream();
            int byteCount = 0;
            while ((byteCount = in.read(buf)) > 0) {
                uncompressedBytes.write(buf, 0, byteCount);
            }
            final ByteBuffer byteBuffer = ByteBuffer.wrap(uncompressedBytes.toByteArray());
            final ByteBufferDecoder decoder = new ByteBufferDecoder(byteBuffer, byteBuffer.capacity());
            return new String(decoder.getCharArray());
        } finally {
            FileUtilities.close(in);
        }
    }
    
    public synchronized List<String> listIdentifiersStartingWith(String prefix) {
        ArrayList<String> result = new ArrayList<>();
        for (String identifier : uniqueIdentifiers) {
            if (identifier.startsWith(prefix)) {
                result.add(identifier);
            }
        }
        return result;
    }
    
    private String makeResult(String wordAtCaretOrSelection) {
        if (wordAtCaretOrSelection.startsWith("import ")) {
            return makePackageResult(wordAtCaretOrSelection);
        } else if (wordAtCaretOrSelection.matches("^[A-Z][A-Za-z0-9_]*$")) {
            return new ClassDoc(wordAtCaretOrSelection).getClassSummary();
        } else if (wordAtCaretOrSelection.matches("^[a-z][A-Za-z0-9_]*$")) {
            return listMethodsOrFields(wordAtCaretOrSelection);
        }
        return "";
    }
    
    private class ClassDoc {
        private String className;
        private Collection<Class<?>> classes;
        
        private ClassDoc(String className) {
            this.className = className;
            this.classes = JavaDoc.getClasses(className);
        }
        
        private String getClassSummary() {
            return getSummary(true, false, false);
        }
        
        private String getConstructorSummary() {
            return getSummary(false, true, false);
        }
        
        private String getStaticSummary() {
            return getSummary(true, false, true);
        }
        
        private String getSummary(boolean listClasses, boolean listConstructors, boolean listMembers) {
            if (classes.isEmpty()) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            for (Class<?> klass : classes) {
                if (result.length() > 0) {
                    result.append("<br><br>");
                }
                if (listClasses) {
                    result.append(listClass(klass));
                }
                if (listConstructors) {
                    result.append(listConstructors(klass));
                }
                if (listMembers) {
                    result.append(listMembers(klass));
                }
            }
            return result.toString();
        }
    }
    
    private String makePackageResult(String text) {
        Pattern importPattern = Pattern.compile("import (.+)\\..+;");
        Matcher matcher = importPattern.matcher(text);
        if (matcher.find()) {
            String packageName = matcher.group(1);
            return listPackage(packageName);
        } else {
            return "";
        }
    }
    
    // FIXME: currently unused, but we should try to do something here.
    /*
    private String makeInstanceMethodOrFieldResult(String text) {
        // FIXME: ideally, we'd find the name of the most recent method with unclosed parentheses.
        // So, in "doSomething(getThing(x), " we'd know that "doSomething" is interesting and "getThing" isn't.
        Pattern namePattern = Pattern.compile("(\\S+)\\s*(\\(?).*");
        Matcher matcher = namePattern.matcher(text);
        if (matcher.find()) {
            String methodOrFieldName = matcher.group(1);
            return listMethodsOrFields(methodOrFieldName);
        } else {
            return "";
        }
    }
    */
    
    /**
     * Returns HTML linking to all the classes in the given package.
     */
    private synchronized String listPackage(String packageName) {
        StringBuilder result = new StringBuilder(packageName + " contains:\n");
        String searchTerm = "Class:" + packageName + ".";
        String htmlFile = "";
        for (String line : javaDocSummary) {
            if (line.startsWith("File:")) {
                htmlFile = line.substring(5);
            } else if (line.startsWith(searchTerm)) {
                String className = line.substring(searchTerm.length());
                /* Just check it is actually a class in this package, and not a class in a sub-package. */
                if (Character.isUpperCase(className.charAt(0))) {
                    result.append("<br><a href=\"" + urlFromHtmlFile(htmlFile) + "\">" + className + "</a>\n");
                }
            }
        }
        return result.toString();
    }
    
    private String urlFromHtmlFile(String htmlFile) {
        if (FileUtilities.exists(htmlFile)) {
            // The file exists locally, so we can use it.
            return "file://" + htmlFile;
        } else {
            // The file doesn't exist locally with the name we have cached in "javadoc-summary.txt", so we'll have to get it from the local override, or direct from Sun.
            final String javaDocLocation = Parameters.getString("java.advisor.doc", JavaDoc.SUN_JAVA_DOC_LOCATION);
            return javaDocLocation + htmlFile.replaceAll("^.*/api/", "/");
        }
    }
    
    private synchronized String listMethodsOrFields(String name) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("^[MF]:(" + StringUtilities.regularExpressionFromLiteral(name) + "[^(\t]*)(\\([^\t]+)\t");
        Matcher matcher;
        String htmlFile = "";
        String className = "";
        for (String line : javaDocSummary) {
            if (line.startsWith("File:")) {
                htmlFile = line.substring(5);
            } else if (line.startsWith("Class:")) {
                className = line.substring(6);
            } else if ((matcher = pattern.matcher(line)).find()) {
                String url = urlFromHtmlFile(htmlFile);
                result.append("<br><a href=\"" + url + "#" + matcher.group(1) + matcher.group(2) + "\">" + matcher.group(1) + "</a> in " + makeClassLink(new ClassAndPackage(className), true, true));
            }
        }
        if (result.length() == 0) {
            return "";
        }
        result.insert(0, name + " could be:\n");
        return result.toString();
    }
    
    /** Formats class names as a list of links if there's javadoc for them. */
    private String makeClassLinks(Class<?>[] classes, boolean pkg, String conjunct, boolean showSourceLink) {
        StringBuilder s = new StringBuilder();
        for (Class<?> klass : classes) {
            if (s.length() > 0) {
                s.append(conjunct);
            }
            s.append(makeClassLink(klass, pkg, showSourceLink));
        }
        return s.toString();
    }
    
    private static class ClassAndPackage {
        String className;
        String packageName;
        
        ClassAndPackage(String className, String packageName) {
            this.className = className;
            this.packageName = packageName;
        }
        
        ClassAndPackage(String qualifiedName) {
            this.packageName = "";
            final int dot = qualifiedName.lastIndexOf(".");
            if (dot != -1) {
                this.packageName = qualifiedName.substring(0, dot);
                this.className = qualifiedName.substring(dot + 1);
            }
        }
        
        String qualifiedName() {
            if (packageName.length() == 0) {
                return className;
            }
            return packageName + "." + className;
        }
    }
    
    /** Formats a class name as a link if there's javadoc for it. */
    private String makeClassLink(Class<?> c, boolean showPkg, boolean showSourceLink) {
        ClassAndPackage classAndPackage = new ClassAndPackage(c.getName());
        if (c.isArray()) {
            return makeClassLink(c.getComponentType(), showPkg, showSourceLink) + "[]";
        }
        if (c.isPrimitive()) {
            return c.getName();
        }
        return makeClassLink(classAndPackage, showPkg, showSourceLink);
    }
    
    /** Formats a class name as a link if there's javadoc for it. */
    private String makeClassLink(ClassAndPackage classAndPackage, boolean showPkg, boolean showSourceLink) {
        StringBuilder s = new StringBuilder();
        if (classAndPackage.className.contains(".")) {
            s.append(classAndPackage.className);
            return s.toString();
        }
        s.append(JavaDoc.getDocLink(classAndPackage.className, classAndPackage.packageName, showPkg));
        if (showSourceLink) {
            List<String> sourceFiles = JavaDoc.findSourceFilenames(classAndPackage.qualifiedName());
            for (String sourceFile : sourceFiles) {
                s.append("&nbsp;");
                s.append(JavaDoc.formatAsSourceLink(sourceFile));
            }
        }
        return s.toString();
    }
    
    private static class MemberNameComparator implements Comparator<Member> {
        public int compare(Member lhs, Member rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    }
    
    private String makeMethodLinks(Method[] methods) {
        Arrays.sort(methods, new MemberNameComparator());
        ArrayList<String> list = new ArrayList<>();
        for (Method method : methods) {
            list.add(makeMethodLink(method));
        }
        return StringUtilities.join(list, "");
    }
    
    private String makeFieldLink(Field f) {
        return NEWLINE + makeClassLink(f.getType(), false, false) + " " + f.getName();
    }
    
    private String makeMethodLink(Method m) {
        String parameters = makeClassLinks(m.getParameterTypes(), false, COMMA, false);
        String returnType = makeClassLink(m.getReturnType(), false, false);
        String result = NEWLINE + returnType + " " + m.getName() + "(" + parameters + ")";
        Class<?>[] exceptions = m.getExceptionTypes();
        if (exceptions.length > 0) {
            result += " throws " + makeClassLinks(exceptions, true, NEWLINE, false);
        }
        return result;
    }
    
    /**
    * Lists the fully-qualified class name and all the modifiers,
    * the superclass name and all implemented interfaces.
    */
    private String listClass(Class<?> c) {
        StringBuilder s = new StringBuilder();
        int modifiers = c.getModifiers();
        if (c.isInterface()) {
            modifiers &= ~Modifier.ABSTRACT;
        }
        s.append(Modifier.toString(modifiers));
        if (c.isInterface() == false) {
            s.append(" class");
        }
        s.append("<br>");
        s.append(makeClassLink(c, true, true));
        
        // Show superclasses.
        Class<?> superclass = c.getSuperclass();
        if (superclass != null && c.isInterface() == false) {
            s.append("<br>extends ");
            int depth = 0;
            for (; superclass != null; superclass = superclass.getSuperclass()) {
                s.append(NEWLINE);
                s.append(StringUtilities.nCopies(depth++, INDENT));
                s.append(makeClassLink(superclass, false, true));
            }
        }
        
        // Show all implemented interfaces.
        Class<?>[] interfaces = c.getInterfaces();
        if (interfaces.length > 0) {
            s.append("<br>implements ");
            s.append(NEWLINE);
            s.append(makeClassLinks(interfaces, false, NEWLINE, true));
        }
        
        // Show all interface/superinterface methods.
        if (c.isInterface()) {
            s.append("<br><br>interface methods ");
            s.append(makeMethodLinks(c.getMethods()));
        }
        return s.toString();
    }
    
    /** Lists the class name and all the constructors. */
    private String listConstructors(Class<?> c) {
        StringBuilder s = new StringBuilder();
        String classLink = makeClassLink(c, false, false);
        s.append(makeClassLink(c, true, true));
        s.append("<br>");
        Constructor<?>[] constructors = c.getConstructors();
        for (Constructor<?> constructor : constructors) {
            s.append("<br>");
            s.append(classLink);
            s.append("(");
            s.append(makeClassLinks(constructor.getParameterTypes(), false, COMMA, false));
            s.append(")");
            Class<?>[] exceptions = constructor.getExceptionTypes();
            if (exceptions.length > 0) {
                s.append(NEWLINE);
                s.append(" throws ");
                s.append(makeClassLinks(exceptions, false, NEWLINE, false));
            }
        }
        if (c.isInterface()) {
            s.append(makeMethodLinks(c.getDeclaredMethods()));
        }
        return s.toString();
    }
    
    /**
     * Lists all non-private fields and non-private static methods of a class.
     */
    private String listMembers(Class<?> c) {
        StringBuilder s = new StringBuilder();
        
        List<Field> fields = collectNonPrivateFields(c);
        Collections.sort(fields, new MemberNameComparator());
        if (fields.size() > 0) {
            s.append("<br><br>fields");
            for (Field field : fields) {
                s.append(makeFieldLink(field));
            }
        }
        
        List<Method> methods = collectStaticMethods(c);
        Collections.sort(methods, new MemberNameComparator());
        if (methods.size() > 0) {
            s.append("<br><br>static methods");
            for (Method method : methods) {
                s.append(makeMethodLink(method));
            }
        }
        
        return s.toString();
    }
    
    private List<Field> collectNonPrivateFields(Class<?> c) {
        ArrayList<Field> result = new ArrayList<>();
        for (Field field : c.getFields()) {
            if (Modifier.isPrivate(field.getModifiers()) == false) {
                result.add(field);
            }
        }
        return result;
    }
    
    private List<Method> collectStaticMethods(Class<?> c) {
        ArrayList<Method> result = new ArrayList<>();
        for (Method method : c.getMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isPrivate(modifiers) == false && Modifier.isStatic(modifiers)) {
                result.add(method);
            }
        }
        return result;
    }

//
// WorkspaceResearcher interface.
//
    
    /** Returns true for Java files. */
    public boolean isSuitable(FileType fileType) {
        return fileType == FileType.JAVA;
    }
    
    public String research(String string) {
        return makeResult(string);
    }
    
    /** We don't implement any non-standard URI schemes. */
    public boolean handleLink(String link) {
        return false;
    }
}
