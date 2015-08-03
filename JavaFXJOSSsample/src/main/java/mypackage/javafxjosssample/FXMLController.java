package mypackage.javafxjosssample;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;
import org.javaswift.joss.model.Account;
import org.javaswift.joss.model.Container;
import org.javaswift.joss.model.StoredObject;

public class FXMLController implements Initializable {
    
    @FXML
    private TextField tfAccount;
    
    @FXML
    private TextField tfPassword;
    
    @FXML
    private TextField tfUrl;
    
    @FXML
    private TreeTableView ttv1;
    
    @FXML
    private TreeTableColumn<ContainerFile, String> ttc1;
    
    @FXML
    private TreeTableColumn<ContainerFile, String> ttc2;
    
    @FXML
    private TreeTableColumn<ContainerFile, String> ttc3;
    
    @FXML
    private BorderPane rootPane;
    
    @FXML
    private HBox hb1;
    
    @FXML
    private TabPane tp1;
    
    @FXML
    private Button bReload;
    
    @FXML
    private Button bSignIn;
    
    private Account account;
    private Collection<Container> containers;
    
    @FXML
    private void clickSignIn(ActionEvent event) {
        System.out.println("You clicked me! ");
        
        try {
            // Keystone認証
            AccountConfig config = new AccountConfig();
            config.setUsername(tfAccount.getText());
            config.setPassword(tfPassword.getText());
            String endPoint = String.join("", "https://", tfUrl.getText(), "/v2.0/tokens");
            config.setAuthUrl(endPoint);
            account = new AccountFactory(config).createAccount();
            
            // コンテナリストの取得
            containers = account.list();
            
            getFiles();
            SingleSelectionModel<Tab> selectionModel = tp1.getSelectionModel();
            selectionModel.select(1);
            bReload.setDisable(false);

            bSignIn.setDisable(true);
            tfAccount.setDisable(true);
            tfPassword.setDisable(true);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("警告");
            alert.setHeaderText("ユーザ認証に失敗しました");
            alert.setContentText("アカウントやパスワードをご確認ください");
            alert.showAndWait();
        }
    }
    
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // TODO
                
        ttv1.addEventHandler(DragEvent.DRAG_OVER, (DragEvent event) -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.ANY);
            }
            event.consume();
        });
        
        ttv1.setRowFactory((tv) -> {
            TreeTableRow<ContainerFile> row = new TreeTableRow<>();
            
            row.setOnDragDropped(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Container c = row.getTreeItem().getValue().getContainer();
                    
                    // アップロード
                    Dragboard db = event.getDragboard();
                    db.getFiles().stream().findFirst().ifPresent((File file) -> {
                        
                        ProgressIndicator pi = new ProgressIndicator();
                        pi.setPrefSize(30, 30);
                        hb1.getChildren().add(pi);
                        
                        Task task = new Task<Void>() {
                            @Override
                            public Void call() {
                                StoredObject upload = c.getObject(file.getName());
                                upload.uploadObject(new File(file.toString()));
                                
                                return null;
                            }
                        };
                        task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                            @Override
                            public void handle(WorkerStateEvent wse) {
                                hb1.getChildren().clear();
                                clickReload();
                            }
                        });
                        Executor executor = Executors.newSingleThreadExecutor();
                        executor.execute(task);
                        
                    });
                    event.setDropCompleted(true);
                }
            });
            
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    System.out.println("index= "+index);
                }
            });
            
            return row;
        });
    }
    
    private void getFiles() {
        
        TreeItem<ContainerFile> parent = new TreeItem<>(new ContainerFile("", "", "", null, null));
        ttv1.setRoot(parent);
        
        System.out.println("Contents:");
        containers.stream().forEach((container) -> {
            System.out.println("【"+container.getName()+"】");
            
            // コンテナの作成
            TreeItem<ContainerFile> root = new TreeItem<>(new ContainerFile(container.getName(), "", "", null, container));
            container.list().stream().forEach((object) -> {
                root.getChildren().add(new TreeItem<>(new ContainerFile(object.getName(), Long.toString(object.getContentLength()), object.getLastModified(), object, container)));
                
                System.out.printf("  %s\n", object.getName());
                System.out.printf("    Type: %s\n    Size: %s\n    Last modifed: %s\n    E-tag: %s\n",
                    object.getContentType(), object.getContentLength(),
                    object.getLastModified(), object.getEtag());
            });
            parent.getChildren().add(root);
            root.setExpanded(true);
        });
        
        ttc1.setCellValueFactory((TreeTableColumn.CellDataFeatures<ContainerFile, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getName()));
        ttc2.setCellValueFactory((TreeTableColumn.CellDataFeatures<ContainerFile, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getSize()));
        ttc3.setCellValueFactory((TreeTableColumn.CellDataFeatures<ContainerFile, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().getDate()));
    }
    
    @FXML
    private void handleMouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            Object obj = ttv1.getSelectionModel().getSelectedItem();
            TreeItem<ContainerFile> item = (TreeItem<ContainerFile>)obj;
            StoredObject sobj = item.getValue().getStoredObject();
            //System.out.println(sobj.getName());
            
            final FileChooser chooser = new FileChooser();
            chooser.setTitle("ダウンロードファイル");
            File saveFile = chooser.showSaveDialog(rootPane.getScene().getWindow());
            
            if (saveFile != null) {
                try {
                    System.out.println(sobj.getName()+" downloading...");
                    InputStream  input  = new BufferedInputStream(sobj.downloadObjectAsInputStream());
                    OutputStream output = new BufferedOutputStream(new FileOutputStream(saveFile.getPath().toString()));

                    // ストリームのコピー（※もっとうまい方法はないかな？）
                    int byte_;
                    while ((byte_=input.read()) != -1) {
                        output.write(byte_);
                    }

                    output.flush();
                    output.close();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    @FXML
    private void clickReload() {
        
        System.out.println("reloading...");
        // コンテナリストの取得
        containers = account.list();
        
        TreeItem<ContainerFile> parent = new TreeItem<>(new ContainerFile("", "", "", null, null));
        ttv1.setRoot(parent);
        
        containers.stream().forEach((container) -> {
            // コンテナの作成
            TreeItem<ContainerFile> root = new TreeItem<>(new ContainerFile(container.getName(), "", "", null, container));
            container.list().stream().forEach((object) -> {
                root.getChildren().add(new TreeItem<>(new ContainerFile(object.getName(), Long.toString(object.getContentLength()), object.getLastModified(), object, container)));
            });
            parent.getChildren().add(root);
            root.setExpanded(true);
        });
    }
    
    @FXML
    private void deleteObject(ActionEvent event) {
        
        Object obj = ttv1.getSelectionModel().getSelectedItem();
        TreeItem<ContainerFile> item = (TreeItem<ContainerFile>)obj;
        StoredObject sobj = item.getValue().getStoredObject();
        //System.out.println(sobj.getName());
        
        Container container = item.getValue().getContainer();
        if (container != null) {
            System.out.println("delete clicked! : "+sobj.getName());
            sobj.delete();
            
            clickReload();
        }
    }
    
    @FXML
    public void setOnKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            clickSignIn(null);
        }
    }
    
    
    
    public class ContainerFile { //ContainerModelか？
        private final StoredObject object;
        private final Container container;
        
        private SimpleStringProperty name;
        public SimpleStringProperty nameProperty() {
            if (name == null) {
                name = new SimpleStringProperty(this, "");
            }
            return name;
        }
        
        private SimpleStringProperty size;
        public SimpleStringProperty sizeProperty() {
            if (size == null) {
                size = new SimpleStringProperty(this, "");
            }
            return size;
        }
        
        private SimpleStringProperty date;
        public SimpleStringProperty dateProperty() {
            if (date == null) {
                date = new SimpleStringProperty(this, "");
            }
            return date;
        }
        
        private ContainerFile(String name, String size, String date, StoredObject object, Container container) {
            this.name = new SimpleStringProperty(name);
            this.size = new SimpleStringProperty(size);
            this.date = new SimpleStringProperty(date);
            this.object = object;
            this.container = container;
        }
        
        public String getName() {
            return name.get();
        }
        public void setName(String fName) {
            name.set(fName);
        }
        
        public String getSize() {
            return size.get();
        }
        public void setSize(String fSize) {
            size.set(fSize);
        }
        
        public String getDate() {
            return date.get();
        }
        public void setDate(String fDate) {
            date.set(fDate);
        }
        
        public StoredObject getStoredObject() {
            return object;
        }
        
        public Container getContainer() {
            return container;
        }
    }
}
