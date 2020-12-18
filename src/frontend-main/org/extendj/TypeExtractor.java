/* Copyright (c) 2005-2008, Torbjorn Ekman
 *               2011-2019, Jesper Öqvist <jesper.oqvist@cs.lth.se>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.extendj;

import org.extendj.ast.ASTNode;
import org.extendj.ast.Opt;
import org.extendj.ast.TypedName;
import org.extendj.ast.VarAccess;
import org.extendj.ast.CompilationUnit;
import org.extendj.ast.Frontend;
import org.extendj.ast.TypeDecl;
import org.extendj.ast.Problem;
import org.extendj.ast.Program;
import org.extendj.ast.AbstractClassfileParser;

import java.util.Collection;
import java.util.Iterator;

/**
 * Answer a type query
 */
public class TypeExtractor extends Frontend {

  /**
   * Entry point.
   * @param args command-line arguments
   */
  public static void main(String args[]) {
    int exitCode = new TypeExtractor().run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /**
   * Initialize the compiler.
   */
  public TypeExtractor() {
    super("TypeExtractor for ExtendJ", ExtendJVersion.getVersion());
  }

  /**
   * @param args command-line arguments
   * @return {@code true} on success, {@code false} on error
   * @deprecated Use run instead!
   */
  @Deprecated
  public static boolean compile(String args[]) {
    return 0 == new JavaDumpTree().run(args);
  }

  @Override
  protected void initOptions() {
    super.initOptions();
    program.options().addKeyValueOption("-pos");
  }

  ASTNode findMatch(ASTNode n, int line, int column) {
    if (n.containsLocation(line, column)) {
      return n;
    }

    while (n != null) {
      ASTNode next = null;
      for (int i = 0; i < n.getNumChild(); ++i) {
	ASTNode child = n.getChild(i);

	if (child.isSynthetic()) {
	  // no reliable source info -> brute force search
	  for (int j = 0; j < child.getNumChild(); ++j) {
	    ASTNode descendant = this.findMatch(child.getChild(j), line, column);
	    if (descendant != null) {
	      child = descendant;
	      break;
	    }
	  }
	}
	if (child.containsLocation(line, column)) {
	  next = child;
	  break;
	}
      }
      n = next;
      if (n != null && n instanceof TypedName) {
	return n;
      }
    }
    return null;
  }

  /**
   * Dump source file abstract syntax trees.
   * @param args command-line arguments
   * @return 0 on success, 1 on error, 2 on configuration error, 3 on system
   */
  public int run(String args[]) {
    program.initBytecodeReader(Program.defaultBytecodeReader());
    program.initJavaParser(Program.defaultJavaParser());
    initOptions();
    int argResult = processArgs(args);

    if (argResult != 0) {
      return argResult;
    }

    Collection<String> files = program.options().files();

    if (program.options().hasOption("-version")) {
      printVersion();
      return EXIT_SUCCESS;
    }

    if (program.options().hasOption("-help") || files.isEmpty()) {
      printUsage();
      return EXIT_SUCCESS;
    }

    String pos = program.options().getValueForOption("-pos");
    if (pos == null && pos.split(",").length == 2) {
      System.err.println("Must pass '-pos=<line>,<column>'");
      return EXIT_ERROR;
    }
    String[] parts = pos.split(",");
    int line = Integer.parseInt(parts[0]);
    int column = Integer.parseInt(parts[1]);

    try {
	// for this code we really only want one source file...
      for (String file : files) {
        program.addSourceFile(file);
      }

      TypeDecl object = program.lookupType("java.lang", "Object");
      if (object.isUnknown()) {
	System.err.println("Error: Java standard library not found!");
	return EXIT_UNHANDLED_ERROR;
      }

      int compileResult = EXIT_SUCCESS;

      // Process source compilation units.
      Iterator<CompilationUnit> iter = program.compilationUnitIterator();
      while (iter.hasNext()) {
        CompilationUnit unit = iter.next();

	// All quickly hacked together...
        for (Problem error : unit.parseErrors()) {
	    System.err.println(error); // maybe disable this?
        }

        for (Problem error : unit.errors()) {
	    System.err.println(error); // maybe disable this?
        }

        for (Problem warning : unit.parseErrors()) {
	    System.err.println(warning); // maybe disable this?
        }
      }
    } catch (AbstractClassfileParser.ClassfileFormatError e) {
      System.err.println(e.getMessage());
      return EXIT_UNHANDLED_ERROR;
    } catch (Throwable t) {
      System.err.println("Fatal exception:");
      t.printStackTrace(System.err);
      return EXIT_UNHANDLED_ERROR;
    } finally {
      if (program.options().hasOption("-profile")) {
        program.printStatistics(System.out);
      }
    }

    // int success = EXIT_ERROR;
    // for (String s : program.answers()) {
    // 	System.out.println(s);
    // 	success = EXIT_SUCCESS;
    // }
    // return success;
    ASTNode node = this.findMatch(program.getChild(0), line, column);
    TypedName n = null;
    if (node != null && node instanceof TypedName) {
      n = (TypedName) node;
    }
    if (n == null) {
      return EXIT_ERROR;
    }
    System.out.println(n.name() + " : " + n.type().fullName());
    return EXIT_SUCCESS;
  }

}
