package net.blitzcube.mlapi;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.blitzcube.mlapi.api.IMultiLineAPI;
import net.blitzcube.mlapi.api.tag.ITagController;
import net.blitzcube.mlapi.listener.PacketListener;
import net.blitzcube.mlapi.listener.ServerListener;
import net.blitzcube.mlapi.renderer.LineEntityFactory;
import net.blitzcube.mlapi.renderer.TagRenderer;
import net.blitzcube.mlapi.tag.Tag;
import net.blitzcube.peapi.api.IPacketEntityAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Created by iso2013 on 5/23/2018.
 */
public final class MultiLineAPI extends JavaPlugin implements IMultiLineAPI {

    private final VisibilityStates states = new VisibilityStates();
    private final Map<Integer, Tag> tags = new HashMap<>();
    private final Multimap<EntityType, ITagController> controllersMap = HashMultimap.create();

    private LineEntityFactory lineFactory;

    @Override
    public void onEnable() {
        IPacketEntityAPI packetAPI = (IPacketEntityAPI) Bukkit.getPluginManager().getPlugin("PacketEntityAPI");
        if (packetAPI == null) {
            throw new IllegalStateException("Failed to start MultiLineAPI! PacketEntityAPI could not be found!");
        }

        this.lineFactory = new LineEntityFactory(packetAPI.getModifierRegistry(), packetAPI.getEntityFactory());

        packetAPI.addListener(new PacketListener(this, tags, states, packetAPI));
        Bukkit.getPluginManager().registerEvents(new ServerListener(this, states, packetAPI), this);

        this.addDefaultTagController(DemoController.getInst(this));
        this.addDefaultTagController(DemoController2.getInst(this));

        this.saveDefaultConfig();

        TagRenderer.init(packetAPI, lineFactory, states, this, this.getConfig());
    }

    @Override
    public Tag getTag(Entity entity) {
        return tags.get(entity.getEntityId());
    }

    @Override
    public Tag createTagIfMissing(Entity entity) {
        int id = entity.getEntityId();

        if (!tags.containsKey(id)) {
            TagRenderer renderer = TagRenderer.createInstance(entity.getType());
            Tag tag = new Tag(entity, renderer, controllersMap.get(entity.getType()), lineFactory, states);
            tags.put(id, tag);

            renderer.getNearby(tag, 1.0).filter(input -> input != entity).forEach(player -> renderer.spawnTag(tag, player, null));
        }

        return tags.get(id);
    }

    @Override
    public void deleteTag(Entity entity) {
        Tag tag = tags.remove(entity.getEntityId());
        if (tag == null) return;

        TagRenderer renderer = tag.getRenderer();
        renderer.getNearby(tag, 1.1).forEach(player -> renderer.destroyTag(tag, player, null));
        renderer.purge(tag);
    }

    @Override
    public boolean hasTag(Entity entity) {
        return tags.containsKey(entity.getEntityId());
    }

    @Override
    public void addDefaultTagController(ITagController controller) {
        EntityType[] autoApply = controller.getAutoApplyFor();
        autoApply = (autoApply != null) ? autoApply : EntityType.values();

        for (EntityType type : autoApply) {
            this.controllersMap.put(type, controller);
        }
    }

    @Override
    public void removeDefaultTagController(ITagController controller) {
        Collection<ITagController> values = controllersMap.values();
        while (values.contains(controller)) {
            values.remove(controller);
        }
    }

    @Override
    public Set<ITagController> getDefaultTagControllers() {
        return new HashSet<>(controllersMap.values());
    }

    @Override
    public Collection<ITagController> getDefaultTagControllers(EntityType type) {
        return controllersMap.get(type);
    }

    @Override
    public void update(Entity entity, Player target) {
        Tag tag = tags.get(entity.getEntityId());
        Preconditions.checkState(tag != null, "This entity does not have a tag associated with it!");

        tag.update(target);
    }

    @Override
    public void update(Entity entity) {
        Tag tag = tags.get(entity.getEntityId());
        Preconditions.checkState(tag != null, "This entity does not have a tag associated with it!");

        tag.update();
    }


    @Override
    public void update(ITagController controller, Player target) {
        this.states.getVisible(target).filter(input -> input.getTagControllers(false).contains(controller))
                .forEach(tag -> tag.update(controller));
    }

    @Override
    public void update(ITagController controller) {
        Bukkit.getOnlinePlayers().forEach(p -> update(controller, p));
    }

    @Override
    public void update(ITagController.TagLine line, Player target) {
        this.states.getVisible(target).forEach(tag -> tag.update(line));
    }

    @Override
    public void update(ITagController.TagLine line) {
        Bukkit.getOnlinePlayers().forEach(player -> update(line, player));
    }

    @Override
    public void updateNames(Player target) {
        this.states.getVisible(target).forEach(tag -> tag.updateName(target));
    }

    @Override
    public void updateNames() {
        this.tags.values().forEach(Tag::updateName);
    }

    public boolean hasDefaultTagControllers(EntityType type) {
        return controllersMap.containsKey(type);
    }

    public boolean hasTag(int entityID) {
        return tags.containsKey(entityID);
    }

    public Collection<Tag> getTags() {
        return tags.values();
    }

    public static class DemoController implements ITagController {

        private static DemoController inst;

        private final MultiLineAPI parent;
        public int refreshes = 15;

        private final TagLine line = new TagLine() {
            @Override
            public String getText(Entity target, Player viewer) {
                return (refreshes % 2 == 0) ? null : "One";
            }

            @Override
            public boolean keepSpaceWhenNull(Entity target) {
                return false;
            }
        };

        private final TagLine line2 = new TagLine() {
            @Override
            public String getText(Entity target, Player viewer) {
                if (refreshes % 3 == 0) return null;
                return refreshes % 2 == 0 ? ChatColor.GREEN + "Two" : "Two";
            }

            @Override
            public boolean keepSpaceWhenNull(Entity target) {
                return true;
            }
        };

        private final TagLine line3 = new TagLine() {
            @Override
            public String getText(Entity target, Player viewer) {
                if (refreshes % 4 == 0) return null;
                return refreshes % 2 == 0 ? ChatColor.GREEN + "Three" : "Three";
            }

            @Override
            public boolean keepSpaceWhenNull(Entity target) {
                return false;
            }
        };

        private final Set<Entity> enabledFor;

        private DemoController(MultiLineAPI parent) {
            this.parent = parent;
            this.enabledFor = new HashSet<>();
        }

        public static DemoController getInst(MultiLineAPI parent) {
            return (inst == null) ? inst = new DemoController(parent) : inst;
        }

        @Override
        public List<TagLine> getFor(Entity target) {
            this.enabledFor.add(target);
            return Arrays.asList(line, line2, line3);
        }

        public void refreshAll() {
            for (Entity entity : enabledFor) {
                if (parent.getTag(entity) == null) continue;

                this.parent.getTag(entity).update(this);
            }
        }

        @Override
        public String getName(Entity target, Player viewer, String previous) {
            return "- " + previous + " -";
        }

        @Override
        public EntityType[] getAutoApplyFor() {
            return EntityType.values();
        }

        @Override
        public JavaPlugin getPlugin() {
            return parent;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public int getNamePriority() {
            return 0;
        }
    }

    public static class DemoController2 implements ITagController {

        private static DemoController2 inst;

        private final MultiLineAPI parent;

        private final TagLine line = new TagLine() {
            @Override
            public String getText(Entity target, Player viewer) {
                return "One TWO";
            }

            @Override
            public boolean keepSpaceWhenNull(Entity target) {
                return false;
            }
        };

        private final TagLine line2 = new TagLine() {
            @Override
            public String getText(Entity target, Player viewer) {
                return null;
            }

            @Override
            public boolean keepSpaceWhenNull(Entity target) {
                return false;
            }
        };

        private final TagLine line3 = new TagLine() {
            @Override
            public String getText(Entity target, Player viewer) {
                return "Three TWO";
            }

            @Override
            public boolean keepSpaceWhenNull(Entity target) {
                return false;
            }
        };

        private final Set<Entity> enabledFor;

        private DemoController2(MultiLineAPI parent) {
            this.parent = parent;
            this.enabledFor = new HashSet<>();
        }

        public static DemoController2 getInst(MultiLineAPI parent) {
            return (inst == null) ? inst = new DemoController2(parent) : inst;
        }

        @Override
        public List<TagLine> getFor(Entity target) {
            this.enabledFor.add(target);
            return Arrays.asList(line, line2, line3);
        }

        public void refreshAll() {
            for (Entity entity : enabledFor) {
                if (parent.getTag(entity) == null) continue;

                this.parent.getTag(entity).update(this);
            }
        }

        @Override
        public String getName(Entity target, Player viewer, String previous) {
            return ChatColor.AQUA + "! " + previous + " !";
        }

        @Override
        public EntityType[] getAutoApplyFor() {
            return EntityType.values();
        }

        @Override
        public JavaPlugin getPlugin() {
            return parent;
        }

        @Override
        public int getPriority() {
            return 50;
        }

        @Override
        public int getNamePriority() {
            return -10;
        }
    }
}
