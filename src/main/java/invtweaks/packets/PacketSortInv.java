package invtweaks.packets;

import java.util.*;
import java.util.function.*;

import com.google.common.collect.*;

import invtweaks.config.*;
import invtweaks.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.util.*;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.items.*;

public class PacketSortInv {
	private boolean isPlayer;
	
	public PacketSortInv(boolean isPlayer) { this.isPlayer = isPlayer; }
	public PacketSortInv(PacketBuffer buf) {
		this(buf.readBoolean());
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			if (isPlayer) {
				Map<String, InvTweaksConfig.Category> cats = InvTweaksConfig.getPlayerCats(ctx.get().getSender());
				InvTweaksConfig.Ruleset rules = InvTweaksConfig.getPlayerRules(ctx.get().getSender());
				
				PlayerInventory inv = ctx.get().getSender().inventory;
				
				List<ItemStack> sl = inv.mainInventory.subList(PlayerInventory.getHotbarSize(), inv.mainInventory.size());
				List<ItemStack> stacks = Utils.collated(sl);
				stacks.sort(Comparator.comparing(ItemStack::getTranslationKey));
				
				ctx.get().getSender().getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP)
				.ifPresent(cap -> {
					Collections.fill(sl, ItemStack.EMPTY);
					
					int index = PlayerInventory.getHotbarSize();
					for (ItemStack stack: stacks) {
						while (!(stack = cap.insertItem(index, stack, false)).isEmpty()) { ++index; }
					}
				});
			} else {
				Container cont = ctx.get().getSender().openContainer;
				// check if an inventory is open
				if (cont != ctx.get().getSender().container) {
					Iterable<Slot> validSlots = () -> cont.inventorySlots.stream()
							.filter(slot -> !(slot.inventory instanceof PlayerInventory))
							.filter(slot -> slot.canTakeStack(ctx.get().getSender()))
							.iterator();
					if (!validSlots.iterator().hasNext()) return;
					List<ItemStack> stacks = Utils.collated(() -> Streams.stream(validSlots)
							.map(slot -> slot.getStack().copy())
							.iterator());
					stacks.sort(Comparator.comparing(ItemStack::getTranslationKey));
					
					// TODO special handling for Nether Chests-esque mods?
					ItemStackHandler stackBuffer = new ItemStackHandler(stacks.size());
					int index = 0;
					for (ItemStack stack: stacks) {
						while (!(stack = stackBuffer.insertItem(index, stack, false)).isEmpty()) { ++index; }
					}
					
					Iterator<Slot> slotIt = validSlots.iterator();
					for (int i=0; i<stackBuffer.getSlots() && !stackBuffer.getStackInSlot(i).isEmpty(); ++i) {
						while (slotIt.hasNext()
								&& !slotIt.next().isItemValid(stackBuffer.getStackInSlot(i))) {
							// do nothing
						}
						if (!slotIt.hasNext()) {
							return; // nope right out of the sort
						}
					}
					
					// sort can be done, execute it
					validSlots.forEach(slot -> slot.putStack(ItemStack.EMPTY));
					slotIt = validSlots.iterator();
					for (int i=0; i<stackBuffer.getSlots() && !stackBuffer.getStackInSlot(i).isEmpty(); ++i) {
						Slot cur = null;
						while (slotIt.hasNext()
								&& !(cur = slotIt.next()).isItemValid(stackBuffer.getStackInSlot(i))) {
							// do nothing
						}
						cur.putStack(stackBuffer.getStackInSlot(i));
					}
				}
			}
		});
	}
	
	public void encode(PacketBuffer buf) {
		buf.writeBoolean(isPlayer);
	}
}
