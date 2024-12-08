package Project.Client.Views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * UserListItem represents a user entry in the user list.
 */
public class UserListItem extends JPanel {
    private JEditorPane textContainer;
    private JPanel statusIndicator = new JPanel(); // Indicator for muted/last sender status - 4thimp

    private boolean isMuted = false; // Track muted status
    private boolean isLastSender = false; // Track last sender status

    /**
     * Constructor to create a UserListItem.
     *
     * @param clientId   The ID of the client.
     * @param clientName The name of the client.
     * @param parent     The parent container to calculate available width.
     */
    public UserListItem(long clientId, String clientName, JPanel parent) {
        this.setLayout(new BorderLayout());
        textContainer = new JEditorPane("text/plain", clientName);
        textContainer.setName(Long.toString(clientId));
        textContainer.setEditable(false);
        textContainer.setBorder(new EmptyBorder(0, 0, 0, 0)); // Add padding
        
        //4thimp
        textContainer.setOpaque(false);
        textContainer.setBorder(BorderFactory.createEmptyBorder());
        textContainer.setBackground(new Color(0, 0, 0, 0));


        //statusIndicator 4thimp
        statusIndicator.setPreferredSize(new Dimension(10, 10));
        statusIndicator.setMinimumSize(statusIndicator.getPreferredSize());
        statusIndicator.setMaximumSize(statusIndicator.getPreferredSize());
        this.add(statusIndicator, BorderLayout.WEST);
        
        
        
    

        // Configure textContainer
        JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, parent);
        int scrollBarWidth = parentScrollPane.getVerticalScrollBar().getPreferredSize().width;
        int availableWidth = parent.getWidth() - scrollBarWidth - 10; // Subtract additional padding
        textContainer.setSize(new Dimension(availableWidth, Integer.MAX_VALUE));
        Dimension d = textContainer.getPreferredSize();
        textContainer.setPreferredSize(new Dimension(availableWidth, d.height));

        textContainer.setOpaque(false);
        textContainer.setBorder(BorderFactory.createEmptyBorder());
        textContainer.setBackground(new Color(0, 0, 0, 0));

        this.add(textContainer, BorderLayout.CENTER);
    }

   
   //4thimp new
    public void setMuted(boolean muted) {
        this.isMuted = muted;
        updateStatusIndicator();
        if (isMuted) {
            textContainer.setForeground(Color.GRAY);
        } else {
            textContainer.setForeground(Color.BLACK);
        }
        revalidate();
        repaint();
    }

     //4thimp 
    public void setLastSender(boolean lastSender) {
        this.isLastSender = lastSender;
        updateStatusIndicator(); // Update the indicator
    }


    //4thimp 
    private void updateStatusIndicator() {
        if (isMuted) {
            statusIndicator.setBackground(Color.GRAY); 
        } else if (isLastSender) {
            statusIndicator.setBackground(Color.BLUE); 

        } else {
            statusIndicator.setBackground(new Color(0, 0, 0, 0)); // Default transparent
        }
        revalidate();
        repaint();
    }

    
}