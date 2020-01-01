package invtweaks.packets;

import java.util.*;
import java.util.function.*;

import com.electronwill.nightconfig.core.*;

import invtweaks.*;
import invtweaks.config.*;
import net.minecraft.network.*;
import net.minecraftforge.fml.network.*;

public class PacketUpdateConfig {
	private final List<UnmodifiableConfig> cats;
	private final List<String> rules;
	
	public PacketUpdateConfig() { this(Collections.emptyList(), Collections.emptyList()); }
	public PacketUpdateConfig(List<UnmodifiableConfig> cats, List<String> rules) {
		this.cats = cats;
		this.rules = rules;
	}
	public PacketUpdateConfig(PacketBuffer buf) {
		this.cats = new ArrayList<>();
		int catsSize = buf.readVarInt();
		for (int i=0; i<catsSize; ++i) {
			CommentedConfig subCfg = CommentedConfig.inMemory();
			subCfg.set("name", buf.readString());
			List<String> spec = new ArrayList<>();
			int specSize = buf.readVarInt();
			for (int j=0; j<specSize; ++j) {
				spec.add(buf.readString());
			}
			subCfg.set("spec", spec);
			cats.add(subCfg);
		}
		this.rules = new ArrayList<>();
		int rulesSize = buf.readVarInt();
		for (int i=0; i<rulesSize; ++i) {
			rules.add(buf.readString());
		}
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			Map<String, InvTweaksConfig.Category> catsMap = new LinkedHashMap<>();
			for (UnmodifiableConfig subCfg: cats) {
				if (!subCfg.getOrElse("name", "").equals("")) {
					catsMap.put(subCfg.get("name"),
							new InvTweaksConfig.Category(subCfg.getOrElse("spec", Collections.<String>emptyList())
									));
				}
			}
			InvTweaksConfig.setPlayerCats(ctx.get().getSender(), catsMap);
			InvTweaksConfig.setPlayerRules(ctx.get().getSender(), new InvTweaksConfig.Ruleset(rules));
			
			InvTweaksMod.LOGGER.info("Received config from client!"); // TODO remove in production
		});
	}
	
	public void encode(PacketBuffer buf) {
		buf.writeVarInt(cats.size());
		for (UnmodifiableConfig subCfg: cats) {
			buf.writeString(subCfg.getOrElse("name", ""));
			List<String> spec = subCfg.getOrElse("spec", Collections.<String>emptyList());
			buf.writeVarInt(spec.size());
			for (String subSpec: spec) {
				buf.writeString(subSpec);
			}
		}
		
		buf.writeVarInt(rules.size());
		for (String subRule: rules) {
			buf.writeString(subRule);
		}
	}
}
