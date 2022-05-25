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
package org.openrewrite.maven;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.TagNameComparator;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = true)
public class AddProperty extends Recipe {

    @Option(displayName = "Key",
            description = "The name of the property key to be added.",
            example = "junit.version")
    String key;

    @Option(displayName = "Value",
            description = "The value of property to be added.",
            example = "4.13")
    String value;

    @Option(displayName = "Preserve value if property exists",
            description = "Preserve previous value if the property already exists in the pom file.",
            required = false,
            example = "false")
    @Nullable
    Boolean preserveExistingValue;

    @Override
    public String getDisplayName() {
        return "Add Maven project property";
    }

    @Override
    public String getDescription() {
        return "Add a new property to the Maven project property.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            final String propertyName = key.replace("${", "").replace("}", "");

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document d = super.visitDocument(document, ctx);
                Xml.Tag root = d.getRoot();
                Optional<Xml.Tag> properties = root.getChild("properties");
                if (!properties.isPresent()) {
                    Xml.Tag propertiesTag = Xml.Tag.build("<properties>\n<" + propertyName + ">" + value + "</" + propertyName + ">\n</properties>");
                    d = (Xml.Document) new AddToTagVisitor<ExecutionContext>(root, propertiesTag, new MavenTagInsertionComparator(root.getChildren())).visitNonNull(d, ctx);
                } else if (!properties.get().getChildValue(propertyName).isPresent()) {
                    Xml.Tag propertyTag = Xml.Tag.build("<" + propertyName + ">" + value + "</" + propertyName + ">");
                    d = (Xml.Document) new AddToTagVisitor<>(properties.get(), propertyTag, new TagNameComparator()).visitNonNull(d, ctx);
                }
                return d;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (!Boolean.TRUE.equals(preserveExistingValue)
                        && isPropertyTag() && propertyName.equals(tag.getName())
                        && !value.equals(tag.getValue().orElse(null))) {
                    doAfterVisit(new ChangeTagValueVisitor<>(tag, value));
                }
                return super.visitTag(tag, ctx);
            }
        };
    }
}