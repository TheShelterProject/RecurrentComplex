/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.reccomplex.world.gen.feature.structure.generic.maze.rules;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import ivorius.ivtoolkit.maze.components.*;
import ivorius.ivtoolkit.tools.GuavaCollectors;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by lukas on 05.10.15.
 */
public class ReachabilityStrategy<M extends MazeComponent<C>, C> implements MazePredicate<M, C>
{
    private final Set<Pair<MazeRoom, Set<MazeRoom>>> traversalAbilities = new HashSet<>();

    private ConnectionPoint mainConnectionPoint;
    private final List<ConnectionPoint> connectionPoints = new ArrayList<>();
    private final TObjectIntMap<ConnectionPoint> stepsReached = new TObjectIntHashMap<>();

    private final Predicate<MazeRoom> confiner;
    private final Predicate<C> traverser;

    private boolean preventConnection;

    public ReachabilityStrategy(Predicate<MazeRoom> confiner, Predicate<C> traverser, boolean preventConnection)
    {
        this.confiner = confiner;
        this.traverser = traverser;
        this.preventConnection = preventConnection;
    }

    public static <M extends MazeComponent<C>, C> ReachabilityStrategy<M, C> connect(Collection<Collection<MazePassage>> points, Predicate<C> traverser, Predicate<MazeRoom> confiner, Set<Pair<MazeRoom, Set<MazeRoom>>> traversalAbilities)
    {
        ReachabilityStrategy<M, C> strategy = new ReachabilityStrategy<>(confiner, traverser, false);
        strategy.setConnection(points);
        strategy.traversalAbilities.addAll(traversalAbilities);
        return strategy;
    }

    public static <M extends MazeComponent<C>, C> ReachabilityStrategy<M, C> preventConnection(Collection<Collection<MazePassage>> points, Predicate<C> traverser, Predicate<MazeRoom> confiner)
    {
        ReachabilityStrategy<M, C> strategy = new ReachabilityStrategy<>(confiner, traverser, true);
        strategy.setConnection(points);
        return strategy;
    }

    public static <C> Set<Pair<MazeRoom, Set<MazeRoom>>> compileAbilities(Collection<? extends MazeComponent<C>> components, Predicate<C> traverser)
    {
        Set<Pair<MazeRoom, Set<MazeRoom>>> abilities = new HashSet<>();

        for (MazeComponent<C> component : components)
        {
            // Walking within the component, and at last outside
            for (MazePassage source : component.reachability().keySet())
            {
                // TODO Don't use a traversed Set since we don't need it
                for (MazePassage exit : traverse(Collections.singleton(component), new HashSet<>(), Collections.singleton(source), traverser, null))
                {
                    // Only if we can exit the component here it's a true ability
                    if (!component.rooms().contains(exit.getSource()))
                    {
                        MazePassage passage = new MazePassage(source.getSource(), exit.getSource());
                        abilities.add(Pair.of(passage.normalize().getDest(), component.rooms().stream().map(r -> r.sub(passage.getSource())).collect(Collectors.toSet())));
                    }
                }
            }
        }

//         An ability starts where you can place a room, and stops where you can place the next room
        // Remove inferrable abilities
        for (Iterator<Pair<MazeRoom, Set<MazeRoom>>> iterator = abilities.iterator(); iterator.hasNext(); )
        {
            Pair<MazeRoom, Set<MazeRoom>> ability = iterator.next();
            MazeRoom nullRoom = new MazeRoom(new int[ability.getLeft().getDimensions()]);

            if (approximateCanReach(ability.getRight(),
                    abilities.stream().filter(a -> !a.equals(ability)).collect(Collectors.toSet()),
                    Collections.singleton(nullRoom),
                    Collections.singleton(ability.getLeft())
                    , null))
                iterator.remove();
        }

        return abilities;
    }

    public static <C> Predicate<C> connectorTraverser(final Set<C> blockingConnections)
    {
        return input -> !blockingConnections.contains(input);
    }

    protected static <C> Set<MazePassage> traverse(Collection<MazeComponent<C>> mazes, @Nonnull Collection<MazePassage> traversed, Collection<MazePassage> connections, Predicate<C> traverser, @Nullable Consumer<MazePassage> visitor)
    {
        if (connections.size() <= 0)
            return Collections.emptySet();

        Deque<MazePassage> dirty = new ArrayDeque<>(connections);
        Set<MazePassage> added = new HashSet<>();

        MazePassage src;
        while ((src = dirty.pollFirst()) != null)
        {
            for (MazeComponent<C> maze : mazes)
            {
                maze.reachability().get(src).forEach(dest ->
                {
                    // Have we been here already?
                    if (traversed.add(dest))
                    {
                        if (visitor != null) visitor.accept(dest);
                        added.add(dest);
                        dirty.addLast(dest);

                        // Try to go through path
                        MazePassage rDest = dest.inverse();
                        if (traverser.test(maze.exits().get(dest)) && traversed.add(rDest))
                        {
                            // We are now on the other side of the connection/'wall'
                            if (visitor != null) visitor.accept(rDest);
                            added.add(rDest);
                            dirty.addLast(rDest);
                        }
                    }
                });
            }
        }
        return added;
    }

    private static boolean approximateCanReach(Set<MazeRoom> rooms, Set<Pair<MazeRoom, Set<MazeRoom>>> abilities, Set<MazeRoom> left, Set<MazeRoom> right, Predicate<MazeRoom> confiner)
    {
        return approximateCanReach(rooms, abilities, Collections.emptyList(), left, right, Collections.emptyList(), confiner, null);
    }

    private static <C> boolean approximateCanReach(Set<MazeRoom> rooms, Set<Pair<MazeRoom, Set<MazeRoom>>> abilities, Collection<MazeComponent<C>> mazes, Set<MazeRoom> left, Set<MazeRoom> right, Collection<MazePassage> pTraversed, Predicate<MazeRoom> confiner, Predicate<C> traverser)
    {
        if (left.size() <= 0 || right.size() <= 0)
            return false;

        // This actually might happen
        if (left.stream().anyMatch(right::contains))
            return true;

        final Collection<MazePassage> traversed = Sets.newHashSet(pTraversed); // Editable

        Predicate<MazeRoom> predicate = confiner != null ? confiner.and((o) -> !rooms.contains(o)) : rooms::contains;
        Predicate<MazePassage> passagePredicate = p -> predicate.test(p.getDest()) && !traversed.contains(p);

        Multimap<MazeRoom, MazePassage> mazeEntries = compileEntries(mazes, passagePredicate, traverser);

        Set<MazeRoom> visited = Sets.newHashSet(left);
        TreeSet<MazeRoom> dirty = Sets.newTreeSet((o1, o2) ->
        {
            int compare = Double.compare(minDistanceSQ(o1, right), minDistanceSQ(o2, right));
            return compare != 0 ? compare : compare(o1.getCoordinates(), o2.getCoordinates());
        });
        dirty.addAll(left);
        visited.addAll(left);

        MazeRoom curPre;
        while ((curPre = dirty.pollFirst()) != null)
        {
            MazeRoom cur = curPre;

            // Try each ability (i.e. walk through empty space)
            for (Pair<MazeRoom, Set<MazeRoom>> pair : (Iterable<Pair<MazeRoom, Set<MazeRoom>>>) abilities.stream()
                    .filter(ability -> ability.getValue().stream().map(cur::add).allMatch(predicate))::iterator)
            {
                MazeRoom room = pair.getKey().add(cur);
                if (right.contains(room))
                    return true;
                if (predicate.test(room) && visited.add(room))
                    dirty.add(room);
            }

            // Try entries (i.e. walk through placed components)
            // TODO Can move from here to the next entry?
            for (MazeRoom room : (Iterable<MazeRoom>)
                    traverse(mazes, traversed, mazeEntries.get(cur), traverser, null).stream()
                            .map(MazePassage::getSource) // If we can go through, we'll be 'source' from the other side
                            .distinct()::iterator)
            {
                if (right.contains(room))
                    return true;
                if (predicate.test(room) && visited.add(room))
                    dirty.add(room);
            }
        }

        return false;
    }

    private static <C> Multimap<MazeRoom, MazePassage> compileEntries(Collection<MazeComponent<C>> mazes, Predicate<MazePassage> passagePredicate, Predicate<C> traverser)
    {
        Multimap<MazeRoom, MazePassage> reachability = HashMultimap.create();
        for (MazeComponent<C> maze : mazes)
            reachability.putAll(maze.reachability().keySet().stream()
                    .filter(passagePredicate.and(p -> traverser.test(maze.exits().get(p))))
                    .collect(GuavaCollectors.toMultimap(MazePassage::getDest, maze.reachability()::get))
            );
        return reachability;
    }

    private static int compare(int[] left, int[] right)
    {
        for (int i = 0; i < left.length; i++)
        {
            int cmp = Integer.compare(left[i], right[i]);
            if (cmp != 0)
                return cmp;
        }

        return 0;
    }

    private static double minDistanceSQ(MazeRoom room, Collection<MazeRoom> rooms)
    {
        return rooms.stream().mapToDouble(room::distanceSQ).min().orElseThrow(InternalError::new);
    }

    protected void setConnection(Collection<Collection<MazePassage>> points)
    {
        connectionPoints.addAll(points.stream().map(p -> new ConnectionPoint(p, p.stream().map(MazePassage::inverse).collect(Collectors.toList()))).collect(Collectors.toList()));

        mainConnectionPoint = connectionPoints.size() > 0 ? connectionPoints.remove(0) : null;
    }

    @Override
    public boolean canPlace(final MorphingMazeComponent<C> maze, final ShiftedMazeComponent<M, C> component)
    {
        if (preventConnection && !stepsReached.isEmpty())
            return true; // Already Connected: Give Up

        if (stepsReached.size() == connectionPoints.size())
            return true; // Done

        Predicate<MazeRoom> isDirtyPre = dirtyRooms(maze.rooms());

        boolean[] unconnectable = new boolean[connectionPoints.size()];
        for (int i = 0; i < connectionPoints.size(); i++)
        {
            ConnectionPoint point = connectionPoints.get(i);
            if (point.traversed.stream().map(MazePassage::getSource).noneMatch(isDirtyPre))
                unconnectable[i] = true; // Has no more openings! It's either reached or given up.
        }

        place(maze, component, true);

        final Set<MazeRoom> roomsFromBoth = Sets.union(maze.rooms(), component.rooms());
        Predicate<MazeRoom> isDirty = dirtyRooms(roomsFromBoth);

        boolean canPlace;
        if (preventConnection)
            canPlace = stepsReached.isEmpty();
        else
        {
            canPlace = true;
            for (int i = 0; i < connectionPoints.size(); i++)
            {
                ConnectionPoint point = connectionPoints.get(i);
                canPlace &= stepsReached.containsKey(point) || unconnectable[i] || approximateCanReach(roomsFromBoth,
                        traversalAbilities,
                        Arrays.asList(maze, component),
                        // Use getSource here since we need to have been on the other side if we want to connect
                        point.traversed.stream().map(MazePassage::getSource).filter(isDirty).collect(Collectors.toSet()),
                        mainConnectionPoint.traversed.stream().map(MazePassage::getSource).filter(isDirty).collect(Collectors.toSet()),
                        point.traversed,
                        confiner,
                        traverser);
            }
        }

        unplace(maze, component, true);

        return canPlace;
    }

    @Nonnull
    protected Predicate<MazeRoom> dirtyRooms(Set<MazeRoom> r)
    {
        return input -> confiner.test(input) && !r.contains(input);
    }

    @Override
    public void willPlace(MorphingMazeComponent<C> maze, ShiftedMazeComponent<M, C> component)
    {
        place(maze, component, false);
    }

    @Override
    public void didPlace(MorphingMazeComponent<C> maze, ShiftedMazeComponent<M, C> component)
    {
    }

    @Override
    public void willUnplace(MorphingMazeComponent<C> maze, ShiftedMazeComponent<M, C> component)
    {

    }

    protected void place(MorphingMazeComponent<C> maze, ShiftedMazeComponent<M, C> component, boolean simulate)
    {
        if (stepsReached.size() == connectionPoints.size())
            stepsReached.transformValues(i -> i + 1);
        else
        {
            for (ConnectionPoint point : connectionPoints)
            {
                if (stepsReached.containsKey(point))
                    stepsReached.adjustValue(point, 1);
                else
                    point.order.add(traverse(maze, component, point.traversed, mainConnectionPoint.traversed, p -> stepsReached.put(point, 0)));
            }

            mainConnectionPoint.order.add(traverse(maze,
                    component,
                    mainConnectionPoint.traversed,
                    connectionPoints.stream().filter(point -> !stepsReached.containsKey(point)).flatMap(point -> point.traversed.stream()).collect(Collectors.toList()),
                    p -> connectionPoints.stream().filter(point -> point.traversed.contains(p)).forEach(point -> stepsReached.put(point, 0))));
        }
    }

    protected Set<MazePassage> traverse(MazeComponent<C> maze, MazeComponent<C> component, Set<MazePassage> traversed, final Collection<MazePassage> goal, Consumer<MazePassage> goalConsumer)
    {
        return traverse(Arrays.asList(maze, component), traversed, Sets.intersection(component.exits().keySet(), traversed), traverser, connection ->
        {
            if (goal.contains(connection))
                goalConsumer.accept(connection);
        });
    }

    @Override
    public void didUnplace(MorphingMazeComponent<C> maze, ShiftedMazeComponent<M, C> component)
    {
        unplace(maze, component, false);
    }

    protected void unplace(MorphingMazeComponent<C> maze, ShiftedMazeComponent<M, C> component, boolean simulate)
    {
        stepsReached.transformValues(i -> i - 1);
        stepsReached.retainEntries((a, i) -> i >= 0);

        if (stepsReached.size() < connectionPoints.size())
        {
            mainConnectionPoint.reverseStep();

            connectionPoints.stream().filter(point -> point.order.size() > mainConnectionPoint.order.size()).forEach(ConnectionPoint::reverseStep);
        }
    }

    @Override
    public boolean isDirtyConnection(MazeRoom dest, MazeRoom source, C c)
    {
        return true;
    }

    protected Function<MazeRoom, String> dirtyMarker(MazeComponent component, MazeComponent place)
    {
        return r ->
        {
            if (isDirty(r, mainConnectionPoint, component))
                return "0";
            else
            {
                int p = connectionPoints.stream().filter(point -> isDirty(r, point, component))
                        .mapToInt(connectionPoints::indexOf).findFirst().orElse(-1);

                if (p >= 0)
                    return "" + (p + 1);
            }

            if (place.rooms().contains(r))
                return "O";

            return null;
        };
    }

    private boolean isDirty(MazeRoom r, ConnectionPoint point, MazeComponent<?> component)
    {
        return !stepsReached.containsKey(point) && point.traversed.stream()
                .map(MazePassage::getSource)
                .filter(dirtyRooms(component.rooms()))
                .anyMatch(r::equals);
    }

    private static class ConnectionPoint
    {
        public final Set<MazePassage> traversed = new HashSet<>();
        public final List<Set<MazePassage>> order = new ArrayList<>();

        @SafeVarargs
        public ConnectionPoint(Collection<MazePassage>... points)
        {
            Arrays.stream(points).forEach(traversed::addAll);
        }

        public void reverseStep()
        {
            traversed.removeAll(order.remove(order.size() - 1));
        }
    }
}
