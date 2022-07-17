/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package generate;

import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.StringJoiner;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
public class WritePrinter extends Recipe {
    final List<J.ClassDeclaration> modelClasses;

    @Override
    public String getDisplayName() {
        return "Write the boilerplate for `CobolPrinter`";
    }

    @Override
    public String getDescription() {
        return "Every print method starts with `visitSpace` then `visitMarkers`. " +
                "Every model element is visited. An engineer must fill in the places " +
                "where keywords are grammatically required.";
    }

    Supplier<JavaParser> parser = () -> JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()).build();

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;

                for (J.ClassDeclaration modelClass : missingVisitorMethods(c)) {
                    String modelTypeName = modelClass.getSimpleName();
                    String paramName = modelTypeName.substring(0, 1).toLowerCase() + modelTypeName.substring(1);

                    StringJoiner fields = new StringJoiner("\n    ");
                    for (Statement statement : modelClass.getBody().getStatements()) {
                        if (statement instanceof J.VariableDeclarations) {
                            J.VariableDeclarations varDec = (J.VariableDeclarations) statement;
                            J.VariableDeclarations.NamedVariable namedVariable = varDec.getVariables().get(0);
                            String name = namedVariable.getSimpleName();
                            String elemTypeName = requireNonNull(varDec.getTypeExpression()).printTrimmed(getCursor());
                            String capitalizedName = name.substring(0, 1).toUpperCase() + name.substring(1);

                            JavaType.FullyQualified elemType = requireNonNull(TypeUtils.asFullyQualified(varDec.getType()));
                            switch (elemType.getClassName()) {
                                case "CobolLeftPadded":
                                    fields.add("visitLeftPadded(" + paramName + ".getPadding().get" + capitalizedName + "(), p);");
                                    break;
                                case "CobolRightPadded":
                                    fields.add("visitRightPadded(" + paramName + ".getPadding().get" + capitalizedName + "(), p);");
                                    break;
                                case "CobolContainer":
                                    fields.add("visitContainer(\"\", " + paramName + ".getPadding().get" + capitalizedName + "(), \" \", \"\", p);");
                                    break;
                                case "List":
                                    String loopVar = paramName.substring(0, 1);
                                    if (loopVar.equals("p")) {
                                        loopVar = "pp";
                                    }
                                    String typeParam = ((J.Identifier) ((J.ParameterizedType) varDec.getTypeExpression()).getTypeParameters().get(0)).getSimpleName();
                                    fields.add("for(Cobol." + typeParam + " " + loopVar + " : " + paramName + ".get" + capitalizedName + "()) {\n" +
                                            "    // TODO print each element\n" +
                                            "}");
                                    break;
                                default:
                                    if(elemType.getClassName().startsWith("Cobol")) {
                                        fields.add("visit(" + paramName + ".get" + capitalizedName + "(), p);");
                                    }
                            }
                        }
                    }

                    final JavaTemplate visitMethod = JavaTemplate.builder(this::getCursor, "" +
                            "public Cobol visit#{}(Cobol.#{} #{}, PrintOutputCapture<P> p) {" +
                            "    visitSpace(#{}.getPrefix(), p);" +
                            "    #{}" +
                            "    return #{};" +
                            "}").javaParser(parser).build();

                    c = c.withTemplate(visitMethod, c.getBody().getCoordinates().lastStatement(),
                            modelTypeName, modelTypeName, paramName,
                            paramName,
                            fields,
                            paramName);
                }

                return c;
            }

            private List<J.ClassDeclaration> missingVisitorMethods(J.ClassDeclaration visitorClass) {
                return ListUtils.map(modelClasses, modelClass -> {
                    for (Statement statement : visitorClass.getBody().getStatements()) {
                        if (statement instanceof J.MethodDeclaration) {
                            J.MethodDeclaration m = (J.MethodDeclaration) statement;
                            if (m.getSimpleName().endsWith(modelClass.getSimpleName())) {
                                return null;
                            }
                        }
                    }
                    return modelClass;
                });
            }

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                return super.visitCompilationUnit(cu, executionContext);
            }
        };
    }
}
