/***************************************************************************
 * Bytecode Viewer (BCV) - Java & Android Reverse Engineering Suite        *
 * Copyright (C) 2014 Konloch - Konloch.com / BytecodeViewer.com           *
 *                                                                         *
 * This program is free software: you can redistribute it and/or modify    *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation, either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>. *
 ***************************************************************************/

package the.bytecode.club.bytecodeviewer.decompilers.impl;

import me.konloch.kontainer.io.DiskWriter;
import org.objectweb.asm.tree.ClassNode;
import the.bytecode.club.bytecodeviewer.BytecodeViewer;
import the.bytecode.club.bytecodeviewer.Configuration;
import the.bytecode.club.bytecodeviewer.api.ExceptionUI;
import the.bytecode.club.bytecodeviewer.decompilers.AbstractDecompiler;
import the.bytecode.club.bytecodeviewer.gui.components.JFrameConsolePrintStream;
import the.bytecode.club.bytecodeviewer.resources.ExternalResources;
import the.bytecode.club.bytecodeviewer.translation.TranslatedStrings;
import the.bytecode.club.bytecodeviewer.util.ExceptionUtils;
import the.bytecode.club.bytecodeviewer.util.TempFile;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import static the.bytecode.club.bytecodeviewer.Constants.NL;
import static the.bytecode.club.bytecodeviewer.translation.TranslatedStrings.ERROR;

/**
 * Javap disassembler
 *
 * https://github.com/Konloch/bytecode-viewer/issues/93
 *
 * @author Konloch
 * @since 07/11/2021
 */

public class JavapDisassembler extends AbstractDecompiler
{
    public JavapDisassembler()
    {
        super("Javap Disassembler", "javap");
    }

    @Override
    public String decompileClassNode(ClassNode cn, byte[] bytes)
    {
        if (!ExternalResources.getSingleton().hasJavaToolsSet())
            return "Set Java Tools Path!";

        return disassembleJavaP(cn, bytes);
    }

    private synchronized String disassembleJavaP(ClassNode cn, byte[] bytes)
    {
        TempFile tempFile = null;
        String exception = "This decompiler didn't throw an exception - this is probably a BCV logical bug";

        JFrameConsolePrintStream sysOutBuffer;

        try
        {
            //create the temporary files
            tempFile = TempFile.createTemporaryFile(true, ".class");
            File tempClassFile = tempFile.getFile();

            //write the bytes to the class-file
            DiskWriter.replaceFileBytes(tempClassFile.getAbsolutePath(), bytes, false);

            //load java tools into a temporary classloader
            URLClassLoader child = new URLClassLoader(new URL[]{new File(Configuration.javaTools).toURI().toURL()}, this.getClass().getClassLoader());

            //setup reflection
            Class<?> javap = child.loadClass("com.sun.tools.javap.Main");
            Method main = javap.getMethod("main", String[].class);

            //pipe sys out
            sysOutBuffer = new JFrameConsolePrintStream("", false);

            //silence security manager debugging
            BytecodeViewer.sm.silenceExec(true);

            //invoke Javap
            try
            {
                main.invoke(null, (Object) new String[]{"-p", //Shows all classes and members
                    "-c", //Prints out disassembled code
                    //"-l", //Prints out line and local variable tables
                    "-constants", //Shows static final constants
                    tempClassFile.getAbsolutePath()});
            }
            catch (InvocationTargetException e)
            {
                //expected warning behaviour on JDK-15
            }

            //return output
            sysOutBuffer.finished();
            return sysOutBuffer.getTextAreaOutputStreamOut().getBuffer().toString();
        }
        catch (IllegalAccessException e)
        {
            return TranslatedStrings.ILLEGAL_ACCESS_ERROR.toString();
        }
        catch (Throwable e)
        {
            exception = NL + NL + ExceptionUtils.exceptionToString(e);
        }
        finally
        {
            BytecodeViewer.sm.silenceExec(false);

            if(tempFile != null)
                tempFile.delete();
        }

        return "JavaP " + ERROR + "! " + ExceptionUI.SEND_STACKTRACE_TO + NL + NL
            + TranslatedStrings.SUGGESTED_FIX_DECOMPILER_ERROR + NL + NL + exception;
    }

    @Override
    public void decompileToZip(String sourceJar, String zipName)
    {
    }
}
