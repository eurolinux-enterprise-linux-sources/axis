/*
 * Copyright 2001-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.axis.wsdl.toJava;

import org.apache.axis.Version;
import org.apache.axis.utils.Messages;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Emitter knows about WSDL writers, one each for PortType, Binding, Service,
 * Definition, Type.  But for some of these WSDL types, Wsdl2java generates
 * multiple files.  Each of these files has a corresponding writer that extends
 * JavaWriter.  So the Java WSDL writers (JavaPortTypeWriter, JavaBindingWriter,
 * etc.) each calls a file writer (JavaStubWriter, JavaSkelWriter, etc.) for
 * each file that that WSDL generates.
 * <p/>
 * <p>For example, when Emitter calls JavaWriterFactory for a Binding Writer, it
 * returns a JavaBindingWriter.  JavaBindingWriter, in turn, contains a
 * JavaStubWriter, JavaSkelWriter, and JavaImplWriter since a Binding may cause
 * a stub, skeleton, and impl template to be generated.
 * <p/>
 * <p>Note that the writers that are given to Emitter by JavaWriterFactory DO NOT
 * extend JavaWriter.  They simply implement Writer and delegate the actual
 * task of writing to extensions of JavaWriter.
 * <p/>
 * <p>All of Wsdl2java's Writer implementations follow a common behaviour.
 * JavaWriter is the abstract base class that dictates this common behaviour.
 * Many of the files generated are .java files, so this abstract class -
 * JavaClassWriter - exists.  It extends JavaWriter and adds a bit of Java-
 * relative behaviour.  This behaviour is primarily placed within the generate
 * method.  The generate method calls, in succession (note:  the starred methods
 * are the ones you are probably most interested in):
 * <dl>
 * <dt> getFileName
 * <dd> This method is abstract in JavaWriter, but JavaClassWriter implements
 * this method.  Subclasses should have no need to override it.  It
 * returns the fully-qualified file name based on the fully-qualified
 * classname + ".java".
 * <dt> isFileGenerated(file)
 * <dd> You should not need to override this method.  It checks to see whether
 * this file is in the List returned by emitter.getGeneratedFileNames.
 * <dt> registerFile(file)
 * <dd> You should not need to override this method.  It registers this file by
 * calling emitter.getGeneratedFileInfo().add(...).
 * <dt> * verboseMessage(file)
 * <dd> You may override this method if you want to provide more information.
 * The generate method only calls verboseMessage if verbose is turned on.
 * <dt> getPrintWriter(file)
 * <dd> You should not need to override this method.  Given the file name, it
 * creates a PrintWriter for it.
 * <dt> writeFileHeader(pw)
 * <dd> JavaClassWriter implements this method, so you should not need to
 * override it.  This method generates a javadoc giving the filename and
 * a comment stating that this file is generated by WSDL2Java, and it
 * generates the class definition including the opening curly brace..
 * <dt> * writeFileBody(pw)
 * <dd> This is an abstract method that must be implemented by the subclass.
 * This is where the body of a file is generated.
 * <dt> * writeFileFooter(pw)
 * <dd> JavaClassWriter implements this method, so you should not need to
 * override it.  It generates the closing curly brace for the class.
 * <dt> closePrintWriter(pw)
 * <dd> You should not need to override this method.  It simply closes the
 * PrintWriter.
 * </dl>
 * <p/>
 * Additional behaviour that JavaClassWriter introduces beyond JavaWriter is
 * related to the class header and definition:
 * <dl>
 * <dt> writeHeaderComments
 * <dd> Write the header comments, such as the file name and that the file was
 * generated by WSDL2Java.  You need not override this method unless you
 * want a tailored comment.
 * <dt> writePackage
 * <dd> Write the package statement, if necessary.  You should not need to
 * override this method.
 * <dt> getClassModifiers
 * <dd> Modifiers, such as "public", "final", "abstract" would be returned by
 * this method.  The default implementation only generates "public ", so
 * any subclass that needs more must override this method.
 * <dt> getClassText
 * <dd> This simply returns "class ".  If anything else is desired, for
 * instance, JavaInterfaceWriter prefers "interface ", then this method
 * must be overridden.
 * <dt> getExtendsText
 * <dd> The default implementation returns "".  If a subclass desires to list
 * a set of classes this one extends, then this method must be overridden.
 * <dt> getImplementsText
 * <dd> Same as getExtendsText except for the implements clause.
 * </dl>
 */
public abstract class JavaClassWriter extends JavaWriter {

    /** Field namespaces */
    protected Namespaces namespaces;

    /** Field className */
    protected String className;

    /** Field packageName */
    protected String packageName;

    /**
     * Constructor.
     * 
     * @param emitter       The emitter instance
     * @param fullClassName The fully qualified class name of the class
     *                      to be generated.
     * @param type          
     */
    protected JavaClassWriter(Emitter emitter, String fullClassName,
                              String type) {

        super(emitter, type);

        this.namespaces = emitter.getNamespaces();
        this.packageName = Utils.getJavaPackageName(fullClassName);
        this.className = Utils.getJavaLocalName(fullClassName);
    }    // ctor

    /**
     * Return the file name as a string of the form:
     * "<directory-ized fully-qualified classname>.java"
     * 
     * @return 
     */
    protected String getFileName() {
        return namespaces.toDir(packageName) + className + ".java";
    }    // getFileName

    /**
     * You should not need to override this method.
     * It registers the given file by calling
     * emitter.getGeneratedFileInfo().add(...).
     * JavaClassWriter overrides this method from JavaWriter because
     * it add class name to the registration information.
     * 
     * @param file 
     */
    protected void registerFile(String file) {

        final String pkg = getPackage();
        String fqClass;
        if (pkg != null && pkg.length() > 0) {
            fqClass = pkg + '.' + getClassName();
        } else {
            fqClass = getClassName();
        }

        emitter.getGeneratedFileInfo().add(file, fqClass, type);
    }    // registerFile

    /**
     * Write a common header, including the package name, the class
     * declaration, and the opening curly brace.
     * 
     * @param pw 
     * @throws IOException 
     */
    protected void writeFileHeader(PrintWriter pw) throws IOException {

        writeHeaderComments(pw);
        writePackage(pw);

        // print class declaration
        pw.println(getClassModifiers() + getClassText() + getClassName() + ' '
                + getExtendsText() + getImplementsText() + "{");
    }    // writeFileHeader

    /**
     * Write the header comments.
     * 
     * @param pw 
     * @throws IOException 
     */
    protected void writeHeaderComments(PrintWriter pw) throws IOException {

        String localFile = getFileName();
        int lastSepChar = localFile.lastIndexOf(File.separatorChar);

        if (lastSepChar >= 0) {
            localFile = localFile.substring(lastSepChar + 1);
        }

        pw.println("/**");
        pw.println(" * " + localFile);
        pw.println(" *");
        pw.println(" * " + Messages.getMessage("wsdlGenLine00"));
        pw.println(" * "
                + Messages.getMessage("wsdlGenLine01",
                        Version.getVersionText()));
        pw.println(" */");
        pw.println();
    }    // writeHeaderComments

    /**
     * Write the package declaration statement.
     * 
     * @param pw 
     * @throws IOException 
     */
    protected void writePackage(PrintWriter pw) throws IOException {

        final String pkg = getPackage();
        if (pkg != null && pkg.length() > 0) {
            pw.println("package " + pkg + ";");
            pw.println();
        }
    }    // writePackage

    /**
     * Return "public ".  If more modifiers are needed, this method must be
     * overridden.
     * 
     * @return 
     */
    protected String getClassModifiers() {
        return "public ";
    }    // getClassModifiers

    /**
     * Return "class ".  If "interface " is needed instead, this method must be
     * overridden.
     * 
     * @return 
     */
    protected String getClassText() {
        return "class ";
    }    // getClassString

    /**
     * Returns the appropriate extends clause.  This default implementation
     * simply returns "", but if you want "extends <class/interface list> "
     * then you must override this method.
     * 
     * @return ""
     */
    protected String getExtendsText() {
        return "";
    }    // getExtendsText

    /**
     * Returns the appropriate implements clause.  This default implementation
     * simply returns "", but if you want "implements <interface list> " then
     * you must override this method.
     * 
     * @return ""
     */
    protected String getImplementsText() {
        return "";
    }    // getImplementsText

    /**
     * Returns the package name.
     * 
     * @return 
     */
    protected String getPackage() {
        return packageName;
    }    // getPackage

    /**
     * Returns the class name.
     * 
     * @return 
     */
    protected String getClassName() {
        return className;
    }    // getClassName

    /**
     * Generate the closing curly brace.
     * 
     * @param pw 
     * @throws IOException 
     */
    protected void writeFileFooter(PrintWriter pw) throws IOException {
        super.writeFileFooter(pw);
        pw.println('}');
    }    // writeFileFooter
}    // abstract class JavaClassWriter
