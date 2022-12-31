/*
 * Copyright 2019-2022 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cloudnetservice.ext.platforminject.processor.platform.fabric;

import eu.cloudnetservice.ext.platforminject.api.data.PluginDataParser;
import eu.cloudnetservice.ext.platforminject.api.spi.PlatformDataGeneratorProvider;

public class FabricPlatformInfoProvider extends PlatformDataGeneratorProvider {

  public FabricPlatformInfoProvider() {
    super(
      "fabric",
      FabricPluginInfoGenerator::new,
      FabricMainClassGenerator::new,
      () -> PluginDataParser.create()
        .enableSupport(PluginDataParser.PLUGIN_ID)
        .enableSupport(PluginDataParser.PLUGIN_DEPENDENCIES));
  }
}
