/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.reccomplex.gui.editstructure.preset;

import ivorius.ivtoolkit.tools.IvTranslations;
import ivorius.reccomplex.RecurrentComplex;
import ivorius.reccomplex.gui.GuiValidityStateIndicator;
import ivorius.reccomplex.gui.table.*;
import ivorius.reccomplex.structures.StructureInfos;
import ivorius.reccomplex.utils.PresetRegistry;
import ivorius.reccomplex.utils.presets.PresettedObject;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Created by lukas on 20.09.16.
 */
public class TableDataSourceSavePreset<T> extends TableDataSourceSegmented
{
    public PresettedObject<T> object;

    public String id = "";
    public String title = "";
    public String description = "";

    public TableDelegate delegate;
    public TableNavigator navigator;

    public TableDataSourceSavePreset(PresettedObject<T> object, TableDelegate delegate, TableNavigator navigator)
    {
        this.object = object;
        this.delegate = delegate;
        this.navigator = navigator;

        addManagedSection(0, new TableDataSourceSupplied(() ->
        {
            TableCellString cell = new TableCellString("id", id);
            cell.setShowsValidityState(true);
            cell.setValidityState(currentIDState());
            cell.addPropertyConsumer(s ->
            {
                id = s;
                cell.setValidityState(currentIDState());
                if (object.getPresetRegistry().has(s))
                {
                    title = object.getPresetRegistry().title(s).orElse("");
                    description = object.getPresetRegistry().description(s)
                            .flatMap(d -> d.stream().reduce((s1, s2) -> s1 + "<br>" + s2))
                            .orElse("");
                    TableElements.reloadExcept(delegate, "id");
                }
            });
            return new TableElementCell(IvTranslations.get("reccomplex.preset.id"), cell)
                    .withTitleTooltip(IvTranslations.getLines("reccomplex.preset.id.tooltip"));
        }, () ->
        {
            TableCellString cell = new TableCellString(null, title);
            cell.addPropertyConsumer(s -> title = s);
            return new TableElementCell(IvTranslations.get("reccomplex.preset.title"), cell)
                    .withTitleTooltip(IvTranslations.getLines("reccomplex.preset.title.tooltip"));
        }, () ->
        {
            TableCellString cell = new TableCellString(null, description);
            cell.addPropertyConsumer(s -> description = s);
            return new TableElementCell(IvTranslations.get("reccomplex.preset.description"), cell)
                    .withTitleTooltip(IvTranslations.getLines("reccomplex.preset.description.tooltip").stream()
                            .map(s -> s.replaceAll("<BR>", "<br>")).collect(Collectors.toList()));
        }));
        addManagedSection(1, TableCellMultiBuilder.create(navigator, delegate)
                .addAction(() -> IvTranslations.get("reccomplex.gui.save"), null, this::save).buildDataSource());
    }

    public boolean save()
    {
        if (!StructureInfos.isSimpleID(id))
            return false;

        object.getPresetRegistry().register(id, true, object.getContents(), new PresetRegistry.Metadata(title, description.split("<br>")));

        try
        {
            object.getPresetRegistry().save(id, true);
        }
        catch (IOException e)
        {
            RecurrentComplex.logger.error("Error saving preset: " + id, e);
            return false;
        }

        navigator.popTable();
        object.setPreset(id);

        return true;
    }

    private GuiValidityStateIndicator.State currentIDState()
    {
        return StructureInfos.isSimpleID(id)
                ? object.getPresetRegistry().has(id)
                ? GuiValidityStateIndicator.State.SEMI_VALID
                : GuiValidityStateIndicator.State.VALID
                : GuiValidityStateIndicator.State.INVALID;
    }
}