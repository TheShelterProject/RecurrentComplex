/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.commands;

import ivorius.reccomplex.RCConfig;
import ivorius.reccomplex.capability.SelectionOwner;
import ivorius.reccomplex.utils.ServerTranslations;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

/**
 * Created by lukas on 09.06.14.
 */
public class CommandSelectShift extends CommandSelectModify
{
    @Override
    public String getCommandName()
    {
        return RCConfig.commandPrefix + "shift";
    }

    @Override
    public String getCommandUsage(ICommandSender var1)
    {
        return ServerTranslations.usage("commands.selectShift.usage");
    }

    @Override
    public void executeSelection(ICommandSender sender, SelectionOwner selectionOwner, String[] args) throws CommandException
    {
        if (args.length < 3)
            throw ServerTranslations.wrongUsageException("commands.selectShift.usage");

        int x = parseInt(args[0]), y = parseInt(args[1]), z = parseInt(args[2]);

        selectionOwner.setSelectedPoint1(selectionOwner.getSelectedPoint1().add(x, y, z));
        selectionOwner.setSelectedPoint2(selectionOwner.getSelectedPoint2().add(x, y, z));
    }
}
