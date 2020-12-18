package e.edit;

import e.ptextarea.FileType;
import e.util.*;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class TagReader {
    private static final Pattern TAG_LINE_PATTERN = Pattern.compile("([^\t]+)\t(?:[^\t])+\t(\\d+);\"\t(\\w)(?:\t(.*))?");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(class|enum|interface|namespace|struct|union):([^\t]+).*");
    
    private TagListener listener;
    private FileType fileType;
    private String charsetName;
    
    public TagReader(Path file, FileType fileType, String charsetName, TagListener tagListener) {
        this.listener = tagListener;
        this.fileType = fileType;
        this.charsetName = charsetName;
        
        Path tagsFile = null;
        try {
            // Create the tagsFile here, then pass it in, rather than relying on createTagsFile to do
            // the actual creation. This allows us to know when the tagsFile is present (it's not null),
            // rather than it potentially existing but the tag generation failed. So we can delete it
            // in exactly one place.
            tagsFile = Files.createTempFile("e.edit.TagReader-tags-", ".tags");
            createTagsFile(file, tagsFile);
            readTagsFile(tagsFile);
        } catch (Exception ex) {
            listener.taggingFailed(ex);
        } finally {
            if (tagsFile != null) {
                try {
                    Files.delete(tagsFile);
                } catch (IOException ex) {}
            }
        }
    }
    
    private String chooseCtagsBinary() {
        // We don't cache this to give the user a chance to fix things while we're running.
        // exctags is on FreeBSD.
        for (String candidateCtags : Arrays.asList("ctags-exuberant", "exuberant-ctags", "ectags", "exctags")) {
            if (FileUtilities.findOnPath(candidateCtags) != null) {
                return candidateCtags;
            }
        }
        return "ctags";
    }
    
    private void createTagsFile(Path file, Path tagsFile) throws IOException {
        ArrayList<String> command = new ArrayList<>();
        command.add(chooseCtagsBinary());
        command.add("--c++-types=+p");
        command.add("-n");
        command.add("--fields=+am");
        command.add("-u");
        command.add("--regex-java=/(\\bstatic\\b)/\\1/S/");
        command.add("--regex-c++=/(\\bstatic\\b)/\\1/S/");
        if (fileType != null) {
            command.add("--language-force=" + ctagsLanguageForFileType(fileType));
        }
        command.add("-f");
        command.add(tagsFile.toString());
        command.add(file.toAbsolutePath().toString());
        
        ArrayList<String> errors = new ArrayList<>();
        ProcessUtilities.backQuote(tagsFile.getParent(), command.toArray(new String[command.size()]), errors, errors);
        // We're not actually expecting anything on stdout or stderr from ctags.
        // All the more reason to output anything it has to say!
        for (String error : errors) {
            Log.warn("ctags: " + error);
        }
    }
    
    public static String ctagsLanguageForFileType(FileType fileType) {
        if (fileType == FileType.ASSEMBLER) {
            return "Asm";
        } else if (fileType == FileType.BASH) {
            return "Sh";
        } else if (fileType == FileType.C_PLUS_PLUS) {
            return "C++";
        } else if (fileType == FileType.C_SHARP) {
            return "C#";
        } else if (fileType == FileType.GO) {
            return "Go";
        } else if (fileType == FileType.JAVA) {
            return "Java";
        } else if (fileType == FileType.JAVA_SCRIPT) {
            return "JavaScript";
        } else if (fileType == FileType.MAKE) {
            return "Make";
        } else if (fileType == FileType.RUBY) {
            return "Ruby";
        } else if (fileType == FileType.RUST) {
            return "Rust";
        } else if (fileType == FileType.PERL) {
            return "Perl";
        } else if (fileType == FileType.PHP) {
            return "PHP";
        } else if (fileType == FileType.PYTHON) {
            return "Python";
        } else if (fileType == FileType.EMAIL || fileType == FileType.PATCH || fileType == FileType.PLAIN_TEXT || fileType == FileType.PROTO || fileType == FileType.VHDL || fileType == FileType.XML) {
            return null;
        } else {
            throw new RuntimeException("Don't know what ctags(1) calls \"" + fileType + "\"");
        }
    }
    
    private void readTagsFile(Path tagsFile) throws IOException {
        ValidHeaderChecker checker = new ValidHeaderChecker();
        try (Stream<String> stream = Files.lines(tagsFile)) {
            stream.forEach(v -> checker.processLine(v));
        }
    }
    
    public class ValidHeaderChecker {
        private boolean checkedHeader = false;
        public void processLine(String line) {
            if (!checkedHeader) {
                if (!line.startsWith("!_TAG_")) {
                    throw new RuntimeException("The tags file didn't have a valid header.");
                }
                checkedHeader = true;
                return;
            }
            processTagLine(line);
        }
    }
    
    /**
     * Set to the number of the last line containing the pattern " static ".
     * If the next tag seen has the same line number, it will be marked as static.
     */
    private int staticTagLineNumber = 0;
    
    private void processTagLine(String line) {
        // The format is: <identifier>\t<filename>\t<line>;"\t<tag type>[\t<context>]
        // For example:
        // A   headers/openssl/bn.h    257;"   m   struct:bn_blinding_st
        // Here the tag is called "A", is in "headers/openssl/bn.h" on line 257, and
        // is a 'm'ember of the struct called "bn_blinding_st".
        final Matcher matcher = TAG_LINE_PATTERN.matcher(line);
        if (matcher.matches() == false) {
            return;
        }
        
        final String identifier = matcher.group(1);
        final int lineNumber = Integer.parseInt(matcher.group(2));
        final char type = matcher.group(3).charAt(0);
        String context = matcher.group(4);
        if (context == null) {
            context = "";
        }
        
        if (type == 'S') {
            staticTagLineNumber = lineNumber;
            return;
        }
        
        final Matcher classMatcher = CLASS_PATTERN.matcher(context);
        String containingClassKind = "";
        String containingClass = "";
        if (classMatcher.matches()) {
            containingClassKind = classMatcher.group(1);
            containingClass = classMatcher.group(2);
        }
        //Log.warn(context + " => " + containingClass);
        
        TagReader.Tag tag = null;
        if (fileType == FileType.JAVA) {
            tag = new JavaTag(identifier, lineNumber, type, context, containingClass, containingClassKind);
        } else if (fileType == FileType.C_PLUS_PLUS) {
            tag = new CTag(identifier, lineNumber, type, context, containingClass);
        } else if (fileType == FileType.JAVA_SCRIPT) {
            tag = new JavaScriptTag(identifier, lineNumber, type, context, containingClass);
        } else if (fileType == FileType.PERL) {
            tag = new PerlTag(identifier, lineNumber, type, context, containingClass);
        } else if (fileType == FileType.RUBY) {
            tag = new RubyTag(identifier, lineNumber, type, context, containingClass);
        } else {
            tag = new TagReader.Tag(identifier, lineNumber, type, context, containingClass);
        }
        
        tag.isStatic = ((lineNumber == staticTagLineNumber) || tag.isStatic);
        staticTagLineNumber = 0;
        
        listener.tagFound(tag);
    }
    
    public static class Tag {
        public TagType[][] typeSortOrder = new TagType[][] {
            { TagType.NAMESPACE },
            { TagType.CLASS, TagType.STRUCT, TagType.UNION },
            { TagType.MACRO },
            { TagType.CONSTRUCTOR },
            { TagType.DESTRUCTOR },
            { TagType.PROTOTYPE, TagType.METHOD },
            { TagType.FIELD, TagType.VARIABLE },
            { TagType.ENUM },
            { TagType.TYPEDEF }
        };
        public String classSeparator = ".";
        
        public String identifier;
        public TagType type;
        public String context;
        public String containingClass;
        public int lineNumber;
        public boolean isStatic;
        public boolean isAbstract;
        public boolean isPrototype;
        
        public Tag(String identifier, int lineNumber, char tagType, String context, String containingClass) {
            this.identifier = identifier;
            this.lineNumber = lineNumber;
            this.context = context;
            this.containingClass = containingClass;
            
            this.isAbstract = context.contains("implementation:abstract");
            this.isPrototype = (TagType.fromChar(tagType) == TagType.PROTOTYPE);
            
            // Recognize constructors. Using the same name as the containing
            // class is a pattern common to most languages.
            if (containingClass.equals(identifier)) {
                tagType = 'C';
            }
            this.type = TagType.fromChar(tagType);
        }
        
        public static final Color PUBLIC = Color.GREEN.darker();
        public static final Color PROTECTED = Color.ORANGE;
        public static final Color PRIVATE = new Color(255, 140, 140);
        
        public Color visibilityColor() {
            if (context.contains("access:public")) {
                return PUBLIC;
            } else if (context.contains("access:private") || context.contains("file:")) {
                return PRIVATE;
            } else if (context.contains("access:protected")) {
                return PROTECTED;
            } else {
                return Color.GRAY;
            }
        }
        
        public String describe() {
            return type.describe(identifier);
        }
        
        public String toString() {
            return describe();
        }
        
        public String getSortIdentifier() {
            for (int i = 0; i < typeSortOrder.length; i++) {
                for (int j = 0; j < typeSortOrder[i].length; j++) {
                    if (typeSortOrder[i][j].equals(type)) {
                        return i + identifier + j;
                    }
                }
            }
            return identifier;
        }
        
        public String getClassQualifiedName() {
            if (containingClass.length() > 0) {
               return containingClass + classSeparator + identifier;
            } else {
                return identifier;
            }
        }
    }
    
    public static class JavaTag extends Tag {
        public JavaTag(String identifier, int lineNumber, char tagType, String context, String containingClass, String containingClassKind) {
            super(identifier, lineNumber, tagType, context, containingClass);
            typeSortOrder = new TagType[][] {
                { TagType.PACKAGE },
                { TagType.ENUM_CONSTANT },
                { TagType.FIELD },
                { TagType.CONSTRUCTOR },
                { TagType.METHOD },
                { TagType.CLASS, TagType.ENUM, TagType.INTERFACE }
            };
            
            // Mark interfaces themselves -- and all their methods --  as "abstract" so they're rendered differently.
            if (type == TagType.INTERFACE || containingClassKind.equals("interface")) {
                this.isAbstract = true;
            }
            
            if (containingClass.endsWith("." + identifier)) {
                // An inner class constructor.
                this.type = TagType.CONSTRUCTOR;
            }
        }
    }
    
    public static class JavaScriptTag extends Tag {
        public JavaScriptTag(String identifier, int lineNumber, char tagType, String context, String containingClass) {
            super(identifier, lineNumber, fixType(tagType), context, containingClass);
        }
        
        private static char fixType(char type) {
            switch (type) {
                case 'f': return 'm'; // Function -> Method.
                case 'p': return 'f'; // Property -> Field.
                default: return type;
            }
        }
    }
    
    public static class RubyTag extends Tag {
        public RubyTag(String identifier, int lineNumber, char tagType, String context, String containingClass) {
            super(identifier, lineNumber, fixType(tagType), context, containingClass);
        }
        
        private static char fixType(char type) {
            switch (type) {
                case 'm': return 'M'; // Module, not method.
                default: return type;
            }
        }
    }
    
    public static class PerlTag extends Tag {
        public PerlTag(String identifier, int lineNumber, char tagType, String context, String containingClass) {
            super(identifier, lineNumber, fixType(tagType), context, containingClass);
        }
        
        private static char fixType(char type) {
            switch (type) {
                case 's': return 'm'; // Method (subroutine), not struct.
                default: return type;
            }
        }
    }
    
    public static class CTag extends Tag {
        public CTag(final String identifier, final int lineNumber, final char tagType, final String context, final String containingClass) {
            super(identifier, lineNumber, fixType(tagType), context, containingClass);
            classSeparator = "::";
            
            if (identifier.charAt(0) == '~') {
                this.type = TagType.DESTRUCTOR;
            } else if (containingClass.endsWith("::" + identifier)) {
                // A constructor in a namespace.
                this.type = TagType.CONSTRUCTOR;
            }
            
            // Mark prototypes as "abstract" so they're rendered differently.
            if (this.isPrototype) {
                this.isAbstract = true;
            }
        }
        
        /**
         * The tag type needs to be converted to one of our canonical types,
         * to work around ectags' liberal re-use of type tags. In C++ mode,
         * for example, 'p' means function prototype, in Java it means package.
         * It also means "paragraph", "port", "procedure", "program" and
         * "property". Pascal, Sql and Tcl programmers beware!
         * The likely best work-around for this would be to submit a
         * patch to ectags so that it at least records in its tags
         * file which mode it was in.
         */
        private static char fixType(char type) {
            switch (type) {
                case 'f': return 'm'; // Function -> Method.
                case 'm': return 'f'; // Member (data) -> Field.
                case 'p': return 'P'; // Prototype, not package.
                default: return type;
            }
        }
        
        public Color visibilityColor() {
            if (isStatic && containingClass.length() == 0) {
                return PRIVATE;
            }
            return super.visibilityColor();
        }
    }
    
    public interface TagListener {
        public void tagFound(TagReader.Tag tag);
        
        public void taggingFailed(Exception ex);
    }
}
