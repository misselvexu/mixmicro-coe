/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.trait.member;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.Validated;
import org.openrewrite.ValidationException;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.trait.Element;
import org.openrewrite.java.trait.variable.Parameter;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * A method or constructor.
 */
public interface Callable extends Element {
    @Nullable
    JavaType getReturnType();

    List<Parameter> getParameters();

    static Validated<Callable> of(Cursor cursor) {
        if (cursor.getValue() instanceof J.MethodDeclaration) {
            return Validated.valid("Callable", new MethodDeclarationCallable(
                    cursor,
                    cursor.getValue()
            ));
        }
        return Validated
                .invalid(
                        "cursor",
                        cursor,
                        "Callable must be of type " + J.MethodDeclaration.class +
                                " but was " + cursor.getValue().getClass()
                );
    }
}

@AllArgsConstructor
class MethodDeclarationCallable implements Callable {
    Cursor cursor;

    J.MethodDeclaration methodDeclaration;

    @Getter(lazy = true, onMethod = @__(@Override))
    private final List<Parameter> parameters = collectParameters(cursor, methodDeclaration);

    @Override
    public String getName() {
        return methodDeclaration.getSimpleName();
    }

    @Override
    public JavaType getReturnType() {
        if (methodDeclaration.getReturnTypeExpression() == null) {
            return JavaType.Primitive.Void;
        }
        return methodDeclaration.getReturnTypeExpression().getType();
    }

    @Override
    public UUID getId() {
        return methodDeclaration.getId();
    }

    @Override
    public boolean equals(Object obj) {
        return Element.equals(this, obj);
    }

    private static List<Parameter> collectParameters(Cursor cursor, J.MethodDeclaration methodDeclaration) {
        assert cursor.getValue() == methodDeclaration;
        List<Parameter> parameters = new ArrayList<>(methodDeclaration.getParameters().size());
        new JavaVisitor<List<Parameter>>() {
            {
                // Correctly set the parent cursor for the parameters
                setCursor(cursor);
            }
            @Override
            public J visitVariable(J.VariableDeclarations.NamedVariable variable, List<Parameter> parameters) {
                parameters.add(Parameter.of(getCursor()).getValueNonNullOrThrow());
                return variable;
            }
        }.visit(methodDeclaration.getParameters(), parameters);
        return Collections.unmodifiableList(parameters);
    }
}

/**
 * A compiler-generated initializer method (could be static or
 * non-static), which is used to hold (static or non-static) field
 * initializers, as well as explicit initializer blocks.
 */
@AllArgsConstructor
abstract class InitializerMethod implements Callable {
    /**
     * IMPORTANT: This cursor points to the {@link J.Block} that is the body of the class, NOT the initializer block.
     * This is because the static/object initializer block may not be explicitly declared, and so the cursor would be null.
     */
    Cursor cursor;

    @Override
    public @Nullable JavaType getReturnType() {
        return JavaType.Primitive.Void;
    }

    /**
     * Initializer methods have no parameters.
     */
    @Override
    public List<Parameter> getParameters() {
        return Collections.emptyList();
    }

    @Override
    public UUID getId() {
        return cursor.<Tree>getValue().getId();
    }

    @Override
    public boolean equals(Object obj) {
        return Element.equals(this, obj);
    }
}

/**
 * A static initializer is a method that contains all static
 * field initializations and static initializer blocks.
 */
class StaticInitializerMethod extends InitializerMethod {
    StaticInitializerMethod(Cursor cursor) {
        super(cursor);
    }

    @Override
    public String getName() {
        return "<clinit>";
    }
}

/**
 * An instance initializer is a method that contains field initializations
 * and explicit instance initializer blocks.
 */
class InstanceInitializer extends InitializerMethod {
    InstanceInitializer(Cursor cursor) {
        super(cursor);
    }

    @Override
    public String getName() {
        return "<obinit>";
    }
}
