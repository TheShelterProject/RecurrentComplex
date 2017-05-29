/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.commands;

import com.google.common.collect.Lists;
import ivorius.reccomplex.RCConfig;
import ivorius.reccomplex.utils.BiomeDictionaryAccessor;
import ivorius.reccomplex.utils.ServerTranslations;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by lukas on 03.08.14.
 */
public class CommandBiomeDict extends CommandBase
{
    @Nonnull
    public static Collection<String> keywords(ResourceLocation id, Biome biome)
    {
        return Arrays.asList(id.toString(), biome.getBiomeName());
    }

    @Override
    public String getName()
    {
        return RCConfig.commandPrefix + "biome";
    }

    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender commandSender)
    {
        return ServerTranslations.usage("commands.biomedict.usage");
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) throws CommandException
    {
        if (args.length < 2)
            throw ServerTranslations.wrongUsageException("commands.biomedict.usage");

        switch (args[0])
        {
            case "search":
            {
                CommandSearchStructure.postResultMessage(commandSender,
                        RCTextStyle::biome, CommandSearchStructure.search(Biome.REGISTRY.getKeys(), loc -> CommandSearchStructure.searchRank(Arrays.asList(args), keywords(loc, Biome.REGISTRY.getObject(loc))))
                );
                break;
            }
            case "types":
            {
                String biomeID = args[1];
                Biome biome = Biome.REGISTRY.getObject(new ResourceLocation(biomeID));

                if (biome != null)
                {
                    commandSender.sendMessage(ServerTranslations.format("commands.biomedict.get", biomeID,
                            ServerTranslations.join(Lists.newArrayList(BiomeDictionary.getTypes(biome)).stream()
                                    .map(RCTextStyle::biomeType).toArray())
                    ));
                }
                else
                    commandSender.sendMessage(ServerTranslations.format("commands.biomedict.nobiome", biomeID));
                break;
            }
            case "list":
            {
                BiomeDictionary.Type type = BiomeDictionaryAccessor.getTypeWeak(args[1]);

                if (type != null)
                {
                    commandSender.sendMessage(ServerTranslations.format("commands.biomedict.list", args[1],
                            ServerTranslations.join(Lists.newArrayList(BiomeDictionary.getBiomes(type))
                                    .stream().map(RCTextStyle::biome).toArray())
                    ));
                }
                else
                    commandSender.sendMessage(ServerTranslations.format("commands.biomedict.notype", args[1]));
                break;
            }
            default:
                throw ServerTranslations.wrongUsageException("commands.biomedict.usage");
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos)
    {
        if (args.length == 1)
            return getListOfStringsMatchingLastWord(args, "types", "list", "search");

        if (args[0].equals("types"))
        {
            return getListOfStringsMatchingLastWord(args, Biome.REGISTRY.getKeys());
        }
        else if (args[0].equals("list"))
        {
            return getListOfStringsMatchingLastWord(args, BiomeDictionaryAccessor.getMap().keySet());
        }

        return Collections.emptyList();
    }
}
