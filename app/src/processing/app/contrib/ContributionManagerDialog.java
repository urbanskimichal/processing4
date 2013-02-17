/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along 
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;

import processing.app.*;
import processing.app.contrib.ContributionListing.Filter;


public class ContributionManagerDialog {
  static final String ANY_CATEGORY = "All";

  JFrame dialog;
  String title;
  Filter filter;
  JComboBox categoryChooser;
  JScrollPane scrollPane;
  ContributionListPanel contributionListPanel;
  StatusPanel status;
  FilterField filterField;

  // the calling editor, so updates can be applied
  Editor editor;
  String category;
  ContributionListing contribListing;


  public ContributionManagerDialog(ContributionType type) {
    if (type == null) {
      title = "Update Manager";
    } else {
      title = type.getTitle() + " Manager";
    }
    filter = ContributionListing.createFilter(type);    
    contribListing = ContributionListing.getInstance();
    contributionListPanel = new ContributionListPanel(this, filter);
    contribListing.addContributionListener(contributionListPanel);
  }


  public boolean hasUpdates() {
    return contribListing.hasUpdates();
  }


  public void showFrame(Editor editor) {
    this.editor = editor;

    if (dialog == null) {
      dialog = new JFrame(title);

      Toolkit.setIcon(dialog);

      createComponents();

      registerDisposeListeners();

      dialog.pack();
      Dimension screen = Toolkit.getScreenSize();
      dialog.setLocation((screen.width - dialog.getWidth()) / 2,
                         (screen.height - dialog.getHeight()) / 2);

      contributionListPanel.grabFocus();
    }

    dialog.setVisible(true);

    if (!contribListing.hasDownloadedLatestList()) {
      contribListing.downloadAvailableList(new AbstractProgressMonitor() {
        public void startTask(String name, int maxValue) {
        }

        public void finished() {
          super.finished();

          updateContributionListing();
          updateCategoryChooser();
          if (isError()) {
            status.setErrorMessage("An error occured when downloading " +
                                      "the list of available contributions.");
          } else {
            status.updateUI();
          }
        }
      });
    }
    updateContributionListing();
  }

  
  /**
   * Close the window after an OK or Cancel.
   */
  protected void disposeFrame() {
    dialog.dispose();
    editor = null;
  }
  

  /** Creates and arranges the Swing components in the dialog. */
  private void createComponents() {
    dialog.setResizable(true);

    Container pane = dialog.getContentPane();
    pane.setLayout(new GridBagLayout());

    { // Shows "Filter by Category" and the combo box for selecting a category
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = 0;

      JPanel categorySelector = new JPanel();
      categorySelector.setLayout(new BoxLayout(categorySelector, BoxLayout.X_AXIS));
      pane.add(categorySelector, c);

      categorySelector.add(Box.createHorizontalStrut(6));

      JLabel categoryLabel = new JLabel("Filter by Category:");
      categorySelector.add(categoryLabel);

      categorySelector.add(Box.createHorizontalStrut(5));

      categoryChooser = new JComboBox();
      categoryChooser.setMaximumRowCount(20);
      updateCategoryChooser();
      categorySelector.add(categoryChooser, c);
      categoryChooser.addItemListener(new ItemListener() {

        public void itemStateChanged(ItemEvent e) {
          category = (String) categoryChooser.getSelectedItem();
          if (ContributionManagerDialog.ANY_CATEGORY.equals(category)) {
            category = null;
          }

          filterLibraries(category, filterField.filters);
        }
      });
    }

    { // The scroll area containing the contribution listing and the status bar.
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.BOTH;
      c.gridx = 0;
      c.gridy = 1;
      c.gridwidth = 2;
      c.weighty = 1;
      c.weightx = 1;

      scrollPane = new JScrollPane();
      scrollPane.setPreferredSize(new Dimension(300, 300));
      scrollPane.setViewportView(contributionListPanel);
      scrollPane.getViewport().setOpaque(true);
      scrollPane.getViewport().setBackground(contributionListPanel.getBackground());
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

      status = new StatusPanel();
      status.setBorder(BorderFactory.createEtchedBorder());

      final JLayeredPane layeredPane = new JLayeredPane();
      layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
      layeredPane.add(status, JLayeredPane.PALETTE_LAYER);

      layeredPane.addComponentListener(new ComponentAdapter() {

        void resizeLayers() {
          scrollPane.setSize(layeredPane.getSize());
          scrollPane.updateUI();
        }

        public void componentShown(ComponentEvent e) {
          resizeLayers();
        }

        public void componentResized(ComponentEvent arg0) {
          resizeLayers();
        }
      });

      final JViewport viewport = scrollPane.getViewport();
      viewport.addComponentListener(new ComponentAdapter() {
        void resizeLayers() {
          status.setLocation(0, viewport.getHeight() - 18);

          Dimension d = viewport.getSize();
          d.height = 20;
          d.width += 3;
          status.setSize(d);
        }
        public void componentShown(ComponentEvent e) {
          resizeLayers();
        }
        public void componentResized(ComponentEvent e) {
          resizeLayers();
        }
      });

      pane.add(layeredPane, c);
    }

    { // The filter text area
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = 2;
      c.gridwidth = 2;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      filterField = new FilterField();

      pane.add(filterField, c);
    }

    dialog.setMinimumSize(new Dimension(450, 400));
  }
  

  private void updateCategoryChooser() {
    if (categoryChooser != null) {
      ArrayList<String> categories;
      categoryChooser.removeAllItems();
      categories = new ArrayList<String>(contribListing.getCategories(filter));
//      for (int i = 0; i < categories.size(); i++) {
//        System.out.println(i + " category: " + categories.get(i));
//      }
      Collections.sort(categories);
//    categories.add(0, ContributionManagerDialog.ANY_CATEGORY);
      categoryChooser.addItem(ContributionManagerDialog.ANY_CATEGORY);
      for (String s : categories) {
        categoryChooser.addItem(s);
      }
      categoryChooser.setEnabled(categories.size() != 0);
    }
  }

  
  private void registerDisposeListeners() {
    dialog.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        disposeFrame();
      }
    });
    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        disposeFrame();
      }
    };
    Toolkit.registerWindowCloseKeys(dialog.getRootPane(), disposer);

    // handle window closing commands for ctrl/cmd-W or hitting ESC.

    dialog.getContentPane().addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        //System.out.println(e);
        KeyStroke wc = Toolkit.WINDOW_CLOSE_KEYSTROKE;
        if ((e.getKeyCode() == KeyEvent.VK_ESCAPE) ||
            (KeyStroke.getKeyStrokeForEvent(e).equals(wc))) {
          disposeFrame();
        }
      }
    });
  }

  
  protected void filterLibraries(String category, List<String> filters) {
    List<Contribution> filteredLibraries = 
      contribListing.getFilteredLibraryList(category, filters);
    contributionListPanel.filterLibraries(filteredLibraries);
  }

  
  protected void updateContributionListing() {
    if (editor != null) {
      ArrayList<Library> libraries = new ArrayList<Library>(editor.getMode().contribLibraries);
//    ArrayList<LibraryCompilation> compilations = LibraryCompilation.list(libraries);
//
//    // Remove libraries from the list that are part of a compilations
//    for (LibraryCompilation compilation : compilations) {
//      Iterator<Library> it = libraries.iterator();
//      while (it.hasNext()) {
//        Library current = it.next();
//        if (compilation.getFolder().equals(current.getFolder().getParentFile())) {
//          it.remove();
//        }
//      }
//    }

      ArrayList<Contribution> contributions = new ArrayList<Contribution>();
      contributions.addAll(editor.contribTools);
      contributions.addAll(libraries);
//    contributions.addAll(compilations);

      contribListing.updateInstalledList(contributions);
    }
  }

  
  protected void setFilterText(String filter) {
    if (filter == null || filter.isEmpty()) {
      filterField.setText("");
      filterField.showingHint = true;
    } else {
      filterField.setText(filter);
      filterField.showingHint = false;
    }
    filterField.applyFilter();
  }
  
  
//  private JPanel getPlaceholder() {
//    return contributionListPanel.statusPlaceholder;
//  }
  

  class FilterField extends JTextField {
    final static String filterHint = "Filter your search...";
    boolean showingHint;
    List<String> filters;

    public FilterField () {
      super(filterHint);
      
      showingHint = true;
      filters = new ArrayList<String>();
      updateStyle();

      addFocusListener(new FocusListener() {
        public void focusLost(FocusEvent focusEvent) {
          if (filterField.getText().isEmpty()) {
            showingHint = true;
          }
          updateStyle();
        }

        public void focusGained(FocusEvent focusEvent) {
          if (showingHint) {
            showingHint = false;
            filterField.setText("");
          }
          updateStyle();
        }
      });

      getDocument().addDocumentListener(new DocumentListener() {
        public void removeUpdate(DocumentEvent e) {
          applyFilter();
        }

        public void insertUpdate(DocumentEvent e) {
          applyFilter();
        }

        public void changedUpdate(DocumentEvent e) {
          applyFilter();
        }
      });
    }

    public void applyFilter() {
      String filter = filterField.getFilterText();
      filter = filter.toLowerCase();

      // Replace anything but 0-9, a-z, or : with a space
      filter = filter.replaceAll("[^\\x30-\\x39^\\x61-\\x7a^\\x3a]", " ");
      filters = Arrays.asList(filter.split(" "));
      filterLibraries(category, filters);
    }

    public String getFilterText() {
      return showingHint ? "" : getText();
    }

    public void updateStyle() {
      if (showingHint) {
        setText(filterHint);

        // setForeground(UIManager.getColor("TextField.light")); // too light
        setForeground(Color.gray);
        setFont(getFont().deriveFont(Font.ITALIC));
      } else {
        setForeground(UIManager.getColor("TextField.foreground"));
        setFont(getFont().deriveFont(Font.PLAIN));
      }
    }
  }

  
  public boolean hasAlreadyBeenOpened() {
    return dialog != null;
  }
}