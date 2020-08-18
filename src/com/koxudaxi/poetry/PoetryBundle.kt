/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.koxudaxi.poetry

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

/**
 * This source code is edited by @koxudaxi  (Koudai Aono)
 */
class PoetryBundle : DynamicBundle(bundle) {
    companion object{
        const val bundle = "messages.PoetryBundle"
        private val instance: PoetryBundle = PoetryBundle()

        fun message(@PropertyKey(resourceBundle = bundle) key: String, vararg params: Any): String {
            return instance.getMessage(key, *params)
        }

        fun messagePointer(@PropertyKey(resourceBundle = bundle) key: String, vararg params: Any): Supplier<String> {
            return instance.getLazyMessage(key, *params)
        }
    }
}