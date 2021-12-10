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

package de.dytanic.cloudnet.command.sub;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.annotations.specifier.Quoted;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.command.annotation.CommandAlias;
import de.dytanic.cloudnet.command.annotation.Description;
import de.dytanic.cloudnet.command.exception.ArgumentNotAvailableException;
import de.dytanic.cloudnet.command.source.CommandSource;
import de.dytanic.cloudnet.common.INameable;
import de.dytanic.cloudnet.common.JavaVersion;
import de.dytanic.cloudnet.common.collection.Pair;
import de.dytanic.cloudnet.common.column.ColumnFormatter;
import de.dytanic.cloudnet.common.column.RowBasedFormatter;
import de.dytanic.cloudnet.common.language.I18n;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import de.dytanic.cloudnet.driver.service.ServiceTemplate;
import de.dytanic.cloudnet.driver.template.SpecificTemplateStorage;
import de.dytanic.cloudnet.driver.template.TemplateStorage;
import de.dytanic.cloudnet.template.TemplateStorageUtil;
import de.dytanic.cloudnet.template.install.InstallInformation;
import de.dytanic.cloudnet.template.install.ServiceVersion;
import de.dytanic.cloudnet.template.install.ServiceVersionType;
import de.dytanic.cloudnet.util.JavaVersionResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

@CommandAlias("t")
@CommandPermission("cloudnet.command.templates")
@Description("Manages the templates and allows installation of application files")
public final class CommandTemplate {

  private static final RowBasedFormatter<ServiceTemplate> LIST_FORMATTER = RowBasedFormatter.<ServiceTemplate>builder()
    .defaultFormatter(ColumnFormatter.builder().columnTitles("Storage", "Prefix", "Name").build())
    .column(ServiceTemplate::getStorage)
    .column(ServiceTemplate::getPrefix)
    .column(ServiceTemplate::getName)
    .build();
  private static final RowBasedFormatter<Pair<ServiceVersionType, ServiceVersion>> VERSIONS =
    RowBasedFormatter.<Pair<ServiceVersionType, ServiceVersion>>builder()
      .defaultFormatter(ColumnFormatter.builder()
        .columnTitles("Target", "Name", "Deprecated", "Min Java", "Max Java")
        .build())
      .column(pair -> pair.getFirst().getName())
      .column(pair -> pair.getSecond().getName())
      .column(pair -> pair.getSecond().isDeprecated())
      .column(pair -> pair.getSecond().getMinJavaVersion().orElse(JavaVersion.JAVA_8).getName())
      .column(pair -> pair.getSecond().getMaxJavaVersion().map(JavaVersion::getName).orElse("No maximum"))
      .build();

  @Parser(suggestions = "serviceTemplate")
  public ServiceTemplate defaultServiceTemplateParser(CommandContext<CommandSource> $, Queue<String> input) {
    ServiceTemplate template = ServiceTemplate.parse(input.remove());
    if (template == null || template.knownStorage() == null) {
      throw new ArgumentNotAvailableException(I18n.trans("ca-question-list-invalid-template"));
    }
    return template;
  }

  @Parser
  public ServiceEnvironmentType defaultServiceEnvironmentTypeParser(CommandContext<?> $, Queue<String> input) {
    return CloudNet.getInstance().getServiceVersionProvider().getEnvironmentType(input.remove())
      .orElseThrow(() -> new ArgumentNotAvailableException("No such version type"));
  }

  @Suggestions("serviceTemplate")
  public List<String> suggestServiceTemplate(CommandContext<CommandSource> $, String input) {
    return CloudNet.getInstance().getLocalTemplateStorage().getTemplates()
      .stream()
      .map(ServiceTemplate::toString)
      .collect(Collectors.toList());
  }

  @Parser
  public TemplateStorage defaultTemplateStorageParser(CommandContext<CommandSource> $, Queue<String> input) {
    TemplateStorage templateStorage = CloudNet.getInstance().getTemplateStorage(input.remove());
    if (templateStorage == null) {
      throw new ArgumentNotAvailableException(I18n.trans("ca-question-list-template-invalid-storage"));
    }

    return templateStorage;
  }

  @Suggestions("templateStorage")
  public List<String> suggestTemplateStorage(CommandContext<CommandSource> $, String input) {
    return CloudNet.getInstance().getAvailableTemplateStorages()
      .stream()
      .map(INameable::getName)
      .collect(Collectors.toList());
  }

  @Parser(suggestions = "serviceVersionType")
  public ServiceVersionType defaultVersionTypeParser(CommandContext<CommandSource> $, Queue<String> input) {
    String versionTypeName = input.remove().toLowerCase();
    return CloudNet.getInstance().getServiceVersionProvider().getServiceVersionType(versionTypeName)
      .orElseThrow(() -> new ArgumentNotAvailableException(
        I18n.trans("ca-question-list-invalid-service-version")));
  }

  @Suggestions("serviceVersionType")
  public List<String> suggestServiceVersionType(CommandContext<CommandSource> $, String input) {
    return new ArrayList<>(CloudNet.getInstance().getServiceVersionProvider().getServiceVersionTypes().keySet());
  }

  @Parser(suggestions = "version")
  public ServiceVersion defaultVersionParser(CommandContext<CommandSource> context, Queue<String> input) {
    String version = input.remove();
    ServiceVersionType type = context.get("versionType");

    return type.getVersion(version).orElseThrow(
      () -> new ArgumentNotAvailableException(I18n.trans("command-template-invalid-version")));
  }

  @Suggestions("version")
  public List<String> suggestVersions(CommandContext<CommandSource> context, String input) {
    ServiceVersionType type = context.get("versionType");
    return type.getVersions()
      .stream()
      .filter(ServiceVersion::canRun)
      .map(INameable::getName)
      .collect(Collectors.toList());
  }

  @CommandMethod("template|t list [storage]")
  public void displayTemplates(CommandSource source, @Argument("storage") TemplateStorage templateStorage) {
    Collection<ServiceTemplate> templates;
    // get all templates if no specific template is given
    if (templateStorage == null) {
      templates = CloudNet.getInstance().getServicesRegistry().getServices(TemplateStorage.class).stream()
        .flatMap(storage -> storage.getTemplates().stream())
        .collect(Collectors.toList());
    } else {
      templates = templateStorage.getTemplates();
    }

    source.sendMessage(LIST_FORMATTER.format(templates));
  }

  @CommandMethod("template|t versions|v [versionType]")
  public void displayTemplateVersions(CommandSource source, @Argument("versionType") ServiceVersionType versionType) {
    Collection<Pair<ServiceVersionType, ServiceVersion>> versions;
    if (versionType == null) {
      versions = CloudNet.getInstance().getServiceVersionProvider()
        .getServiceVersionTypes()
        .values().stream()
        .flatMap(type -> type.getVersions().stream()
          .sorted(Comparator.comparing(ServiceVersion::getName))
          .map(version -> new Pair<>(type, version)))
        .collect(Collectors.toList());
    } else {
      versions = CloudNet.getInstance().getServiceVersionProvider().getServiceVersionTypes()
        .get(versionType.getName().toLowerCase())
        .getVersions()
        .stream()
        .sorted(Comparator.comparing(ServiceVersion::getName))
        .map(version -> new Pair<>(versionType, version))
        .collect(Collectors.toList());
    }

    source.sendMessage(VERSIONS.format(versions));
  }

  @CommandMethod("template|t install <template> <versionType> <version>")
  public void installTemplate(
    CommandSource source,
    @Argument("template") ServiceTemplate serviceTemplate,
    @Argument("versionType") ServiceVersionType versionType,
    @Argument("version") ServiceVersion serviceVersion,
    @Flag("force") boolean forceInstall,
    @Flag("executable") @Quoted String executable
  ) {

    String resolvedExecutable = executable == null ? "java" : executable;
    JavaVersion javaVersion = JavaVersionResolver.resolveFromJavaExecutable(resolvedExecutable);
    if (javaVersion == null) {
      source.sendMessage(I18n.trans("ca-question-list-invalid-java-executable"));
      return;
    }

    if (!versionType.canInstall(serviceVersion, javaVersion)) {
      source.sendMessage(I18n.trans("command-template-install-wrong-java")
        .replace("%version%", versionType.getName() + "-" + serviceVersion.getName())
        .replace("%java%", javaVersion.getName())
      );
      if (!forceInstall) {
        return;
      }
    }

    CloudNet.getInstance().getMainThread().runTask(() -> {
      source.sendMessage(I18n.trans("command-template-install-try")
        .replace("%version%", versionType.getName() + "-" + serviceVersion.getName())
        .replace("%template%", serviceTemplate.toString())
      );

      InstallInformation installInformation = InstallInformation.builder(versionType, serviceVersion)
        .toTemplate(serviceTemplate)
        .executable(resolvedExecutable.equals("java") ? null : resolvedExecutable)
        .build();

      if (CloudNet.getInstance().getServiceVersionProvider().installServiceVersion(installInformation, forceInstall)) {
        source.sendMessage(I18n.trans("command-template-install-success")
          .replace("%version%", versionType.getName() + "-" + serviceVersion.getName())
          .replace("%template%", serviceTemplate.toString())
        );
      } else {
        source.sendMessage(I18n.trans("command-template-install-failed")
          .replace("%version%", versionType.getName() + "-" + serviceVersion.getName())
          .replace("%template%", serviceTemplate.toString())
        );
      }
    });

  }

  @CommandMethod("template|t delete|rm|del <template>")
  public void deleteTemplate(CommandSource source, @Argument("template") ServiceTemplate template) {
    SpecificTemplateStorage templateStorage = template.storage();
    if (!templateStorage.exists()) {
      source.sendMessage(I18n.trans("command-template-delete-template-not-found")
        .replace("%template%", template.getFullName())
        .replace("%storage%", template.getStorage()));
      return;
    }

    templateStorage.delete();
    source.sendMessage("Deleted template");
  }

  @CommandMethod("template|t create <template> <environment>")
  public void createTemplate(
    CommandSource source,
    @Argument("template") ServiceTemplate template,
    @Argument("environment") ServiceEnvironmentType environmentType
  ) {
    SpecificTemplateStorage templateStorage = template.storage();
    if (templateStorage.exists()) {
      source.sendMessage(I18n.trans("command-template-create-template-already-exists"));
      return;
    }

    try {
      if (TemplateStorageUtil.createAndPrepareTemplate(template, template.storage(), environmentType)) {
        source.sendMessage(I18n.trans("command-template-create-success")
          .replace("%template%", template.getFullName())
          .replace("%storage%", template.getStorage())
        );
      }
    } catch (IOException exception) {
      source.sendMessage(I18n.trans("command-template-create-failed")
        .replace("%template%", template.getFullName())
        .replace("%storage%", template.getStorage())
      );
    }
  }

  @CommandMethod("template|t copy|cp <sourceTemplate> <targetTemplate>")
  public void copyTemplate(
    CommandSource source,
    @Argument("sourceTemplate") ServiceTemplate sourceTemplate,
    @Argument("targetTemplate") ServiceTemplate targetTemplate
  ) {
    if (sourceTemplate.equals(targetTemplate)) {
      source.sendMessage(I18n.trans("command-template-copy-same-source-and-target"));
      return;
    }

    SpecificTemplateStorage sourceStorage = sourceTemplate.storage();
    SpecificTemplateStorage targetStorage = targetTemplate.storage();

    CloudNet.getInstance().getMainThread().runTask(() -> {
      source.sendMessage(I18n.trans("command-template-copy")
        .replace("%sourceTemplate%", sourceTemplate.toString())
        .replace("%targetTemplate%", targetTemplate.toString())
      );

      targetStorage.delete();
      targetStorage.create();
      try (ZipInputStream stream = sourceStorage.asZipInputStream()) {
        if (stream == null) {
          source.sendMessage(I18n.trans("command-template-copy-failed"));
          return;
        }

        targetStorage.deploy(stream);
        source.sendMessage(I18n.trans("command-template-copy-success")
          .replace("%sourceTemplate%", sourceTemplate.toString())
          .replace("%targetTemplate%", targetTemplate.toString())
        );
      } catch (IOException exception) {
        source.sendMessage(I18n.trans("command-template-copy-failed"));
      }
    });
  }
}
