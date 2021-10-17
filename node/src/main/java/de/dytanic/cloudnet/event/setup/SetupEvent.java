/*
 * Copyright 2019-2021 CloudNetService team & contributors
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

package de.dytanic.cloudnet.event.setup;

import de.dytanic.cloudnet.console.animation.setup.ConsoleSetupAnimation;
import de.dytanic.cloudnet.driver.event.Event;

public class SetupEvent extends Event {

  private final ConsoleSetupAnimation setup;

  public SetupEvent(ConsoleSetupAnimation setup) {
    this.setup = setup;
  }

  public ConsoleSetupAnimation getSetup() {
    return this.setup;
  }
}
