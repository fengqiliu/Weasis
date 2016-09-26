/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d.BulkDragSequence;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.util.MouseEventDouble;

public class GraphicMouseHandler<E extends ImageElement> extends MouseActionAdapter {
    private ViewCanvas<E> vImg;
    private Draggable ds;

    public GraphicMouseHandler(ViewCanvas<E> vImg) {
        if (vImg == null) {
            throw new IllegalArgumentException();
        }
        this.vImg = vImg;
    }

    public Draggable getDragSequence() {
        return ds;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int buttonMask = getButtonMaskEx();

        // Check if extended modifier of mouse event equals the current buttonMask
        // Also asserts that Mouse adapter is not disable
        if (e.isConsumed() || (e.getModifiersEx() & buttonMask) == 0) {
            return;
        }

        // Convert mouse event point to real image coordinate point (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

        // Do nothing and return if current dragSequence is not completed
        if (ds != null && !ds.completeDrag(mouseEvt)) {
            return;
        }

        Cursor newCursor = DefaultView2d.DEFAULT_CURSOR;

        GraphicModel graphicList = vImg.getGraphicManager();
        // Avoid any dragging on selection when Shift Button is Down
        if (!mouseEvt.isShiftDown()) {
            // Evaluates if mouse is on a dragging position, creates a DragSequence and changes cursor consequently
            Optional<Graphic> firstGraphicIntersecting = graphicList.getFirstGraphicIntersecting(mouseEvt);

            if (firstGraphicIntersecting.isPresent() && firstGraphicIntersecting.get() instanceof DragGraphic) {
                DragGraphic dragGraph = (DragGraphic) firstGraphicIntersecting.get();
                List<DragGraphic> selectedDragGraphList = graphicList.getSelectedDragableGraphics();

                if (selectedDragGraphList.contains(dragGraph)) {

                    if (selectedDragGraphList.size() > 1) {
                        ds = new BulkDragSequence(selectedDragGraphList, mouseEvt);
                        newCursor = DefaultView2d.MOVE_CURSOR;

                    } else if (selectedDragGraphList.size() == 1) {

                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            ds = dragGraph.createDragLabelSequence();
                            newCursor = DefaultView2d.HAND_CURSOR;

                        } else {
                            int handlePtIndex = dragGraph.getHandlePointIndex(mouseEvt);

                            if (handlePtIndex >= 0) {
                                dragGraph.moveMouseOverHandlePoint(handlePtIndex, mouseEvt);
                                ds = dragGraph.createResizeDrag(handlePtIndex);
                                newCursor = DefaultView2d.EDIT_CURSOR;

                            } else {
                                ds = dragGraph.createMoveDrag();
                                newCursor = DefaultView2d.MOVE_CURSOR;
                            }
                        }
                    }
                } else {
                    if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                        ds = dragGraph.createDragLabelSequence();
                        newCursor = DefaultView2d.HAND_CURSOR;

                    } else {
                        ds = dragGraph.createMoveDrag();
                        newCursor = DefaultView2d.MOVE_CURSOR;
                    }
                    vImg.getGraphicManager().setSelectedGraphic(Arrays.asList(dragGraph));
                }
            }
        }

        if (ds == null) {
            ImageViewerEventManager<E> eventManager = vImg.getEventManager();
            Optional<ActionW> action = eventManager.getMouseAction(e.getModifiersEx());
            if (action.isPresent() && action.get().isDrawingAction()) {
                Optional<ComboItemListener> items =
                    eventManager.getAction(ActionW.DRAW_CMD_PREFIX + action.get().cmd(), ComboItemListener.class);
                if (items.isPresent()) {
                    Object item = items.get().getSelectedItem();
                    Graphic graph = AbstractGraphicModel.drawFromCurrentGraphic(vImg,
                        (Graphic) (item instanceof Graphic ? item : null));
                    if (graph instanceof DragGraphic) {
                        ds = ((DragGraphic) graph).createResizeDrag();
                        if (!(graph instanceof SelectGraphic)) {
                            vImg.getGraphicManager().setSelectedGraphic(Arrays.asList(graph));
                        }
                    }
                }
            }
        }
        vImg.getJComponent().setCursor(Optional.ofNullable(newCursor).orElse(DefaultView2d.DEFAULT_CURSOR));

        if (ds != null) {
            ds.startDrag(mouseEvt);
        } else {
            vImg.getGraphicManager().setSelectedGraphic(null);
        }

        // Throws to the tool listener the current graphic selection.
        vImg.getGraphicManager().fireGraphicsSelectionChanged(vImg.getMeasurableLayer());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int buttonMask = getButtonMask();

        // Check if extended modifier of mouse event equals the current buttonMask
        // Note that extended modifiers are not triggered in mouse released
        // Also asserts that Mouse adapter is not disable
        if ((e.getModifiers() & buttonMask) == 0) {
            return;
        }

        // Do nothing and return if no dragSequence exist
        if (ds == null) {
            return;
        }

        // Convert mouse event point to real image coordinate point (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

        Optional<SelectGraphic> selection = vImg.getGraphicManager().getSelectGraphic();
        if (selection.isPresent()) {
            Graphic selectGraphic = selection.get();
            AffineTransform transform = DefaultView2d.getAffineTransform(mouseEvt);
            Rectangle selectionRect = selectGraphic.getBounds(transform);

            // Little size rectangle in selection click is interpreted as a single clic
            boolean isSelectionSingleClic =
                selectionRect == null || (selectionRect.width < 5 && selectionRect.height < 5);

            final List<Graphic> newSelectedGraphList = new ArrayList<>();
            if (isSelectionSingleClic) {
                vImg.getGraphicManager().getFirstGraphicIntersecting(mouseEvt).ifPresent(newSelectedGraphList::add);
            } else {
                newSelectedGraphList
                    .addAll(vImg.getGraphicManager().getSelectedAllGraphicsIntersecting(selectionRect, transform));
            }

            // Add all graphics inside selection rectangle at any level in layers instead in the case of single
            // click where top level first graphic found is removed from list if already selected
            if (mouseEvt.isShiftDown()) {
                List<Graphic> selectedGraphList = vImg.getGraphicManager().getSelectedGraphics();

                if (!selectedGraphList.isEmpty()) {
                    if (newSelectedGraphList.isEmpty()) {
                        newSelectedGraphList.addAll(selectedGraphList);
                    } else {
                        selectedGraphList.forEach(g -> {
                            if (!newSelectedGraphList.contains(g)) {
                                newSelectedGraphList.add(g);
                            } else if (isSelectionSingleClic) {
                                newSelectedGraphList.remove(g);
                            }
                        });
                    }
                }
            }

            vImg.getGraphicManager().setSelectedGraphic(newSelectedGraphList);
        }

        if (ds.completeDrag(mouseEvt)) {
            vImg.getEventManager().getAction(ActionW.DRAW_ONLY_ONCE, ToggleButtonListener.class)
                .filter(ToggleButtonListener::isSelected).ifPresent(a -> {
                    vImg.getEventManager().getAction(ActionW.DRAW_MEASURE, ComboItemListener.class)
                        .ifPresent(c -> c.setSelectedItem(MeasureToolBar.selectionGraphic));
                    vImg.getEventManager().getAction(ActionW.DRAW_GRAPHICS, ComboItemListener.class)
                        .ifPresent(c -> c.setSelectedItem(MeasureToolBar.selectionGraphic));
                });
            ds = null;
        }

        // Throws to the tool listener the current graphic selection.
        vImg.getGraphicManager().fireGraphicsSelectionChanged(vImg.getMeasurableLayer());

        Cursor newCursor = DefaultView2d.DEFAULT_CURSOR;

        // Evaluates if mouse is on a dragging position, and changes cursor image consequently
        List<DragGraphic> selectedDragGraphList = vImg.getGraphicManager().getSelectedDragableGraphics();
        Optional<Graphic> firstGraphicIntersecting = vImg.getGraphicManager().getFirstGraphicIntersecting(mouseEvt);

        if (firstGraphicIntersecting.isPresent() && firstGraphicIntersecting.get() instanceof DragGraphic) {
            DragGraphic dragGraph = (DragGraphic) firstGraphicIntersecting.get();

            if (selectedDragGraphList.contains(dragGraph)) {

                if (selectedDragGraphList.size() > 1) {
                    newCursor = DefaultView2d.MOVE_CURSOR;

                } else if (selectedDragGraphList.size() == 1) {
                    if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                        newCursor = DefaultView2d.HAND_CURSOR;

                    } else {
                        if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
                            newCursor = DefaultView2d.EDIT_CURSOR;
                        } else {
                            newCursor = DefaultView2d.MOVE_CURSOR;
                        }
                    }
                }
            } else {
                if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                    newCursor = DefaultView2d.HAND_CURSOR;
                } else {
                    newCursor = DefaultView2d.MOVE_CURSOR;
                }
            }
        }

        vImg.getJComponent().setCursor(Optional.ofNullable(newCursor).orElse(DefaultView2d.DEFAULT_CURSOR));
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int buttonMask = getButtonMaskEx();

        // Check if extended modifier of mouse event equals the current buttonMask
        // Also asserts that Mouse adapter is not disable
        if (e.isConsumed() || (e.getModifiersEx() & buttonMask) == 0) {
            return;
        }

        if (ds != null) {
            // Convert mouse event point to real image coordinate point (without geometric transformation)
            MouseEventDouble mouseEvt = new MouseEventDouble(e);
            mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

            ds.drag(mouseEvt);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (e.isConsumed()) {
            return;
        }
        
        // Convert mouse event point to real image coordinate point (without geometric transformation)
        MouseEventDouble mouseEvt = new MouseEventDouble(e);
        mouseEvt.setImageCoordinates(vImg.getImageCoordinatesFromMouse(e.getX(), e.getY()));

        if (ds != null && (e.getModifiersEx() & getButtonMaskEx()) != 0) {
            ds.drag(mouseEvt);
        } else {

            Cursor newCursor = DefaultView2d.DEFAULT_CURSOR;
            GraphicModel graphicList = vImg.getGraphicManager();

            if (!mouseEvt.isShiftDown()) {
                // Evaluates if mouse is on a dragging position, and changes cursor image consequently
                Optional<Graphic> firstGraphicIntersecting = graphicList.getFirstGraphicIntersecting(mouseEvt);

                if (firstGraphicIntersecting.isPresent() && firstGraphicIntersecting.get() instanceof DragGraphic) {
                    DragGraphic dragGraph = (DragGraphic) firstGraphicIntersecting.get();
                    List<DragGraphic> selectedDragGraphList = vImg.getGraphicManager().getSelectedDragableGraphics();

                    if (selectedDragGraphList.contains(dragGraph)) {

                        if (selectedDragGraphList.size() > 1) {
                            newCursor = DefaultView2d.MOVE_CURSOR;

                        } else if (selectedDragGraphList.size() == 1) {

                            if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                                newCursor = DefaultView2d.HAND_CURSOR;

                            } else {
                                if (dragGraph.getHandlePointIndex(mouseEvt) >= 0) {
                                    newCursor = DefaultView2d.EDIT_CURSOR;
                                } else {
                                    newCursor = DefaultView2d.MOVE_CURSOR;
                                }
                            }
                        }
                    } else {
                        if (dragGraph.isOnGraphicLabel(mouseEvt)) {
                            newCursor = DefaultView2d.HAND_CURSOR;
                        } else {
                            newCursor = DefaultView2d.MOVE_CURSOR;
                        }
                    }
                }
            }
            vImg.getJComponent().setCursor(Optional.ofNullable(newCursor).orElse(DefaultView2d.DEFAULT_CURSOR));
        }
    }
}
