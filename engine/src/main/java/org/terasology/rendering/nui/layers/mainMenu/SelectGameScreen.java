/*
 * Copyright 2018 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.nui.layers.mainMenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.ResourceUrn;
import org.terasology.engine.GameEngine;
import org.terasology.engine.modes.StateLoading;
import org.terasology.engine.paths.PathManager;
import org.terasology.game.GameManifest;
import org.terasology.network.NetworkMode;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.nui.WidgetUtil;
import org.terasology.rendering.nui.databinding.ReadOnlyBinding;
import org.terasology.rendering.nui.layers.mainMenu.gameDetailsScreen.GameDetailsScreen;
import org.terasology.rendering.nui.layers.mainMenu.savedGames.GameInfo;
import org.terasology.rendering.nui.layers.mainMenu.savedGames.GameProvider;
import org.terasology.rendering.nui.widgets.UIButton;
import org.terasology.rendering.nui.widgets.UILabel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SelectGameScreen extends SelectionScreen {
    public static final ResourceUrn ASSET_URI = new ResourceUrn("engine:selectGameScreen");
    private static final String REMOVE_STRING = "saved game";
    private static final Logger logger = LoggerFactory.getLogger(SelectGameScreen.class);

    private UniverseWrapper universeWrapper;

    @Override
    public void initialise() {
        super.initialise();

        UILabel gameTypeTitle = find("gameTypeTitle", UILabel.class);
        if (gameTypeTitle != null) {
            gameTypeTitle.bindText(new ReadOnlyBinding<String>() {
                @Override
                public String get() {
                    if (isLoadingAsServer()) {
                        return translationSystem.translate("${engine:menu#select-multiplayer-game-sub-title}");
                    } else {
                        return translationSystem.translate("${engine:menu#select-singleplayer-game-sub-title}");
                    }
                }
            });
        }

        initSaveGamePathWidget(PathManager.getInstance().getSavesPath());

        getGameInfos().subscribeSelection((widget, item) -> {
            find("load", UIButton.class).setEnabled(item != null);
            find("delete", UIButton.class).setEnabled(item != null);
            find("details", UIButton.class).setEnabled(item != null);
            updateDescription(item);
        });

        getGameInfos().subscribe((widget, item) -> loadGame(item));

        WidgetUtil.trySubscribe(this, "load", button -> {
            final GameInfo gameInfo = getGameInfos().getSelection();
            if (gameInfo != null) {
                loadGame(gameInfo);
            }
        });

        WidgetUtil.trySubscribe(this, "delete", button -> {
            TwoButtonPopup confirmationPopup = getManager().pushScreen(TwoButtonPopup.ASSET_URI, TwoButtonPopup.class);
            confirmationPopup.setMessage(translationSystem.translate("${engine:menu#remove-confirmation-popup-title}"),
                    translationSystem.translate("${engine:menu#remove-confirmation-popup-message}"));
            confirmationPopup.setLeftButton(translationSystem.translate("${engine:menu#dialog-yes}"), this::removeSelectedGame);
            confirmationPopup.setRightButton(translationSystem.translate("${engine:menu#dialog-no}"), () -> { });
        });

        final NewGameScreen newGameScreen = getManager().createScreen(NewGameScreen.ASSET_URI, NewGameScreen.class);
        WidgetUtil.trySubscribe(this, "create", button -> {
            newGameScreen.setUniverseWrapper(universeWrapper);
            getManager().pushScreen(newGameScreen);
        });

        WidgetUtil.trySubscribe(this, "close", button -> triggerBackAnimation());

        WidgetUtil.trySubscribe(this, "details", button -> {
            final GameInfo gameInfo = getGameInfos().getSelection();
            if (gameInfo != null) {
                final GameDetailsScreen detailsScreen = getManager().createScreen(GameDetailsScreen.ASSET_URI, GameDetailsScreen.class);
                detailsScreen.setGameInfo(gameInfo);
                detailsScreen.setPreviews(getPreviewSlideshow().getImages());
                getManager().pushScreen(detailsScreen);
            }
        });
    }

    private void removeSelectedGame() {
        final Path world = PathManager.getInstance().getSavePath(getGameInfos().getSelection().getManifest().getTitle());
        remove(getGameInfos(), world, REMOVE_STRING);
    }

    @Override
    public void onOpened() {
        super.onOpened();

        if (GameProvider.isSavesFolderEmpty()) {
            final NewGameScreen newGameScreen = getManager().createScreen(NewGameScreen.ASSET_URI, NewGameScreen.class);
            newGameScreen.setUniverseWrapper(universeWrapper);
            getManager().pushScreen(newGameScreen);
        }

        if (isLoadingAsServer() && !super.config.getPlayer().hasEnteredUsername()) {
            getManager().pushScreen(EnterUsernamePopup.ASSET_URI, EnterUsernamePopup.class);
        }

        refreshGameInfoList(GameProvider.getSavedGames());
    }

    private void loadGame(GameInfo item) {
        if (isLoadingAsServer()) {
            Path blacklistPath = PathManager.getInstance().getHomePath().resolve("blacklist.json");
            Path whitelistPath = PathManager.getInstance().getHomePath().resolve("whitelist.json");
            if (!Files.exists(blacklistPath)) {
                try {
                    Files.createFile(blacklistPath);
                } catch (IOException e) {
                    logger.error("IO Exception on blacklist generation", e);
                }
            }
            if (!Files.exists(whitelistPath)) {
                try {
                    Files.createFile(whitelistPath);
                } catch (IOException e) {
                    logger.error("IO Exception on whitelist generation", e);
                }
            }
        }
        try {
            final GameManifest manifest = item.getManifest();
            config.getWorldGeneration().setDefaultSeed(manifest.getSeed());
            config.getWorldGeneration().setWorldTitle(manifest.getTitle());
            CoreRegistry.get(GameEngine.class).changeState(new StateLoading(manifest, (isLoadingAsServer()) ? NetworkMode.DEDICATED_SERVER : NetworkMode.NONE));
        } catch (Exception e) {
            logger.error("Failed to load saved game", e);
            getManager().pushScreen(MessagePopup.ASSET_URI, MessagePopup.class).setMessage("Error Loading Game", e.getMessage());
        }
    }

    public boolean isLoadingAsServer() {
        return universeWrapper.getLoadingAsServer();
    }

    public void setUniverseWrapper(UniverseWrapper wrapper) {
        this.universeWrapper = wrapper;
    }

}
