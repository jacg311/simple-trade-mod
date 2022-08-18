package net.jacg.simple_trade;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TradeGui extends SimpleGui {
    public TransactionStatus status = TransactionStatus.TRADING;
    public final Inventory inventory = new SimpleInventory(9) {
        @Override
        public void setStack(int slot, ItemStack stack) {
            super.setStack(slot, stack);
            if (getPartnerByUuid() instanceof ServerPlayerEntity partnerPlayer) {
                if (GuiHelpers.getCurrentGui(partnerPlayer) instanceof TradeGui tradeGui) {
                    tradeGui.setSlot(validSlots[slot] + 4, stack);
                    tradeGui.status = TransactionStatus.TRADING;
                    tradeGui.refreshLockSlot();
                    clearPartnerStatus();
                }
            }
        }
    };
    private final GameProfile partnerProfile;
    private static final int[] validSlots = new int[] {10, 11, 12, 19, 20, 21, 28, 29, 30};

    public TradeGui(ServerPlayerEntity player, GameProfile partnerProfile) {
        super(ScreenHandlerType.GENERIC_9X6, player, false);
        this.partnerProfile = partnerProfile;
    }

    @Override
    public void onOpen() {
        super.onOpen();

        GuiElementBuilder border = new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.empty());
        for (int e = 0; e < 3; e++) {
            for (int i = 1; i < 4; i++) {
                this.setSlot(i * 9 + e * 4, border);
            }
        }

        for (int i = 0; i < 9; i++) {
            this.setSlot(i, border);
        }

        for (int i = 36; i < 54; i++) {
            this.setSlot(i, border);
        }

        for (int i = 0; i < 9; i++) {
            this.setSlotRedirect(validSlots[i], new TradeSlot(inventory, i, 0, 0));
        }

        this.setSlot(2, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setSkullOwner(this.player.getGameProfile(), this.player.getServer())
                .setName(Text.literal("You"))
        );

        this.setSlot(6, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setSkullOwner(partnerProfile, this.player.getServer())
                .setName(Text.literal(partnerProfile.getName()))
        );

        refreshLockSlot();

        clearPartnerStatus();

        this.setSlot(51, new GuiElementBuilder(Items.RED_TERRACOTTA)
                .setName(Text.literal("Abort").styled(style -> style.withColor(Formatting.RED)))
                .setCallback((index, type, action) -> this.setSlot(index, new GuiElementBuilder(Items.RED_TERRACOTTA)
                        .setName(Text.literal("Confirm Abort").styled(style -> style.withColor(Formatting.RED)))
                        .setCallback((index1, type1, action1) -> this.close())
                ))
        );
    }

    private void refreshLockSlot() {
        this.setSlot(47, new GuiElementBuilder(Items.LIME_TERRACOTTA)
                .setName(Text.literal("Lock Items").styled(style -> style.withColor(Formatting.GREEN)))
                .setCallback((index, type, action) -> {
                    if (action == SlotActionType.PICKUP) {
                        this.status = TransactionStatus.LOCKED;

                        if (getPartnerByUuid() instanceof ServerPlayerEntity partnerPlayer) {
                            if (GuiHelpers.getCurrentGui(partnerPlayer) instanceof TradeGui tradeGui) {
                                tradeGui.setPartnerStatusAccepted();
                                if (tradeGui.status == TransactionStatus.LOCKED) {
                                    for (int i = 0; i < 9; i++) {
                                        partnerPlayer.getInventory().offerOrDrop(inventory.removeStack(i));
                                        this.getPlayer().getInventory().offerOrDrop(tradeGui.inventory.removeStack(i));
                                    }
                                    tradeGui.close();
                                    this.close();
                                }
                            }
                        }
                    }
                })
        );
    }

    private PlayerEntity getPartnerByUuid() {
        return this.getPlayer().getWorld().getPlayerByUuid(partnerProfile.getId());
    }

    @Override
    public void onClose() {
        for (int i = 0; i < 9; i++) {
            this.getPlayer().getInventory().offerOrDrop(inventory.removeStack(i));
        }
    }

    public void setPartnerStatusAccepted() {
        this.setSlot(49, new GuiElementBuilder(Items.LIME_TERRACOTTA).setName(Text.literal(partnerProfile.getName() + ": Accepted")));
    }

    public void clearPartnerStatus() {
        this.setSlot(49, new GuiElementBuilder(Items.CYAN_TERRACOTTA)
                .setName(Text.literal("Partner Status"))
        );
    }

    private class TradeSlot extends Slot {
        public TradeSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return status != TransactionStatus.LOCKED;
        }


        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return status != TransactionStatus.LOCKED;
        }
    }

    private enum TransactionStatus {
        LOCKED,
        TRADING,

    }
}
