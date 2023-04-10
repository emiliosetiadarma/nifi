/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.bootstrap.process;

import java.util.ArrayList;
import java.util.List;

public class OperatingSystemConfigurationCheckResult {
    private final String subject;
    private final List<String> explanations;
    private final boolean satisfactory;
    protected OperatingSystemConfigurationCheckResult(final Builder builder) {
        this.subject = builder.subject;
        this.explanations = builder.explanations;
        this.satisfactory = builder.satisfactory;
    }

    public String getSubject() {
        return subject;
    }

    public List<String> getExplanations() {
        return explanations;
    }

    public boolean isSatisfactory() {
        return satisfactory;
    }

    public static final class Builder {
        private String subject = "";
        private List<String> explanations = new ArrayList<>();
        private boolean satisfactory = false;

        public Builder subject(final String subject) {
            if (subject != null) {
                this.subject = subject;
            }
            return this;
        }

        public Builder explanation(final String explanation) {
            if (explanation != null) {
                this.explanations.add(explanation);
            }
            return this;
        }

        public Builder satisfactory(final boolean satisfactory) {
            this.satisfactory = satisfactory;
            return this;
        }

        public OperatingSystemConfigurationCheckResult build() {
            return new OperatingSystemConfigurationCheckResult(this);
        }
    }
}
