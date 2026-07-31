/*
 * Copyright 2026 Immutables Authors and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.immutables.value.processor.meta;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.immutables.value.Value;
import org.immutables.value.processor.Processor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.immutables.check.Checkers.check;

/**
 * Attribute builder helpers are emitted once per value type and named after it, so two attributes
 * reaching one value type by different routes have to resolve to a single helper. Here {@code Foo}
 * is reached through the abstract type and through the generated {@code ImmutableFoo}, which yields
 * two descriptors carrying the same value type name. Deduplicating helper definitions on anything
 * finer than that name emits both under one signature, and the generated source stops compiling.
 * <p>
 * {@code ImmutableFoo} is only detected as buildable once it exists as a class file, so the value
 * type and its consumer are compiled in separate passes.
 */
public class AttributeBuilderHelperNamingTest {

  private static final String FOO = ""
      + "package repro;\n"
      + "import org.immutables.value.Value;\n"
      + "@Value.Immutable\n"
      + "@Value.Style(toBuilder = \"toBuilder\")\n"
      + "public interface Foo {\n"
      + "  String v();\n"
      + "}\n";

  private static final String PARENT = ""
      + "package repro;\n"
      + "import org.immutables.value.Value;\n"
      + "@Value.Immutable\n"
      + "@Value.Style(attributeBuilderDetection = true)\n"
      + "public interface Parent {\n"
      + "  Foo abstractTyped();\n"
      + "  ImmutableFoo immutableTyped();\n"
      + "}\n";

  @Rule
  public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void singleHelperPerValueTypeAcrossDetectionStrategies() throws IOException {
    File annotations = codeSourceOf(Value.class);

    File valueTypeClasses = folder.newFolder("value-type-classes");
    List<String> valueTypeErrors = compile(FOO, "repro.Foo",
        valueTypeClasses, folder.newFolder("value-type-sources"),
        Collections.singletonList(annotations));
    check(valueTypeErrors.toString(), valueTypeErrors.isEmpty());

    List<String> consumerErrors = compile(PARENT, "repro.Parent",
        folder.newFolder("consumer-classes"), folder.newFolder("consumer-sources"),
        Arrays.asList(annotations, valueTypeClasses));
    check(consumerErrors.toString(), consumerErrors.isEmpty());
  }

  /**
   * Compiles a single source with the value processor attached.
   * @return error diagnostics, empty when the source and everything generated from it compiled
   */
  private static List<String> compile(
      String source,
      String typeName,
      File classOutput,
      File sourceOutput,
      List<File> classpath) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
      files.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(classOutput));
      files.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(sourceOutput));
      files.setLocation(StandardLocation.CLASS_PATH, classpath);

      JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics,
          Collections.<String>emptyList(), null,
          Collections.singletonList(new StringSource(typeName, source)));
      task.setProcessors(Collections.singletonList(new Processor()));
      task.call();
    }

    List<String> errors = new ArrayList<>();
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        errors.add(diagnostic.toString());
      }
    }
    return errors;
  }

  private static File codeSourceOf(Class<?> type) {
    try {
      return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  }

  private static final class StringSource extends SimpleJavaFileObject {
    private final String source;

    StringSource(String typeName, String source) {
      super(URI.create("string:///" + typeName.replace('.', '/') + Kind.SOURCE.extension),
          Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
