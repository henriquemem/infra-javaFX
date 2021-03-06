package br.com.datarey.controller;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import br.com.datarey.component.input.CustomInput;
import br.com.datarey.controller.exeption.ImpossivelObterNovaInstanciaException;
import br.com.datarey.controller.exeption.ImpossivelObterValorException;
import br.com.datarey.controller.type.ComponentsType;
import br.com.datarey.databind.Bindable;
import br.com.datarey.databind.DataBind;
import br.com.datarey.exception.BaseException;
import br.com.datarey.util.MessageType;
import br.com.datarey.util.MessageUtil;
import br.com.datarey.util.UtilDataBind;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import jfxtras.labs.scene.control.BeanPathAdapter;

@Bindable
public abstract class AbstractController {

    @SuppressWarnings("rawtypes")
    private Map<String, Map<Field, BeanPathAdapter>> fieldsBean = new HashMap<>();
    private List<Map<String, Field>> listFieldsScene = new ArrayList<>();
    private Map<Field, Field> fieldsProp = new HashMap<>();

    private Map<Integer, Parent> navigator = new HashMap<>();
    
    @Inject
    private MessageUtil messageUtil;

    @FXML
    public void initialize() {
        // pegando super class por causa do Prox do CDI
        Class<?> clazz = this.getClass().getSuperclass();

        initFieldsScene(clazz);
        initFieldsBean(clazz);
        binder();
        init();
    }

    private void initFieldsBean(Class<?> clazz) {

        while(!clazz.equals(AbstractController.class)){
            for (Map<String, Field> fieldsScene : listFieldsScene) {
                for (String fieldSceneName : fieldsScene.keySet()) {
                    forFields(clazz, fieldsScene, fieldSceneName);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void forFields(Class<?> clazz, Map<String, Field> fieldsScene, String fieldSceneName) {
        Map<Field, BeanPathAdapter> map;
        BeanPathAdapter beanPathAdapter;
        Object value;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(UtilDataBind.getFieldsBeanNameFormated(fieldsScene.get(fieldSceneName)))) {
                beanPathAdapter = null;
                value = getValue(field);
                if (fieldSceneName.contains(".")) {
                    map = new HashMap<>();
                    if(value != null)
                        beanPathAdapter = new BeanPathAdapter(value);
                    map.put(field, beanPathAdapter);
                    fieldsBean.put(field.getName(), map);
                } else {
                    fieldsProp.put(fieldsScene.get(fieldSceneName), field);
                }

            }
        }
    }

    Object createNewValue(Field field) {
        try {
            return field.getType().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ImpossivelObterNovaInstanciaException(e);
        }
    }

    void initFieldsScene(Class<?> clazz) {
        Map<String, Field> fieldsScene;

        while(!clazz.equals(AbstractController.class)){
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(DataBind.class)) {
                    fieldsScene = new HashMap<String, Field>();
                    fieldsScene.put(getFieldsBeanName(field), field);
                    listFieldsScene.add(fieldsScene);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void binder() {
        binderListFieldsScene();
        binderFieldsProp();
    }

    @SuppressWarnings("rawtypes")
    void binderListFieldsScene() {
        Map<Field, BeanPathAdapter> mapFieldsBean;
        BeanPathAdapter adapter;
        String beanName;
        Field fieldScene;
        DataBind dataBind;
        for (Map<String, Field> fieldsScene : listFieldsScene) {
            Set<String> fieldsSceneKeys = fieldsScene.keySet();
            for (String fieldsSceneKey : fieldsSceneKeys) {
                if (fieldsSceneKey.contains(".")) {
                    fieldScene = fieldsScene.get(fieldsSceneKey);
                    beanName = UtilDataBind.getFieldsBeanNameFormated(fieldScene);
                    mapFieldsBean = fieldsBean.get(beanName);
                    adapter = mapFieldsBean.get(mapFieldsBean.keySet().iterator().next());
                    if(adapter != null){
                        dataBind = fieldScene.getDeclaredAnnotationsByType(DataBind.class)[0];
                        getComponentsType(fieldScene.getType()).binder(fieldScene, adapter, dataBind, this);
                    }
                }
            }
        }
    }

    private void binderFieldsProp() {
        Object property;
        Set<Field> fieldsPropKeys = fieldsProp.keySet();
        for (Field fieldPropKey : fieldsPropKeys) {
            property = getValue(fieldsProp.get(fieldPropKey));
            if (property != null)
                getComponentsType(fieldPropKey.getType()).binder(fieldPropKey, property, this);
        }
    }

    ComponentsType getComponentsType(Class<?> clazz) {
        for (ComponentsType componentsType : ComponentsType.values()) {
            if (componentsType.getClazz().equals(clazz)) {
                return componentsType;
            }
        }
        Class<?> class2 = clazz;
        while(!class2.equals(Node.class)){
            for(Class<?> item : class2.getInterfaces()){
                if(item.equals(CustomInput.class)){
                    return ComponentsType.CUSTOM_INPUT;
                }
            }
            class2 = class2.getSuperclass();
        }
        messageUtil.showMessage("class:" + clazz.getName() + " DataBind not suported", MessageType.ERROR);
        throw new BaseException("class:" + clazz.getName() + " DataBind not suported");
        
    }

    private String getFieldsBeanName(Field field) {
        return ((DataBind) field.getAnnotationsByType(DataBind.class)[0]).mappedBy();
    }

    Object getValue(Field field) {
        try {
            field.setAccessible(true);
            return field.get(this);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            messageUtil.showMessage(e.getMessage(), MessageType.ERROR);
            throw new ImpossivelObterValorException(e);
        }
    }

    protected void init() {
    }

    protected void addEnterNavigator(Parent node) {
        Integer index = navigator.isEmpty() ? 0 : navigator.size();
        navigator.put(index, node);
        node.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (keyEvent.getCode().equals(KeyCode.ENTER) || keyEvent.getCode().equals(KeyCode.TAB)) {
                Parent prox = navigator.get(index + 1);
                if (prox == null) {
                    prox = navigator.get(0);
                }
                prox.requestFocus();
            }
        });

    }

    @SuppressWarnings("rawtypes")
    Map<String, Map<Field, BeanPathAdapter>> getFieldsBean() {
        return this.fieldsBean;
    }

    Map<Field, Field> getFieldsProp() {
        return this.fieldsProp;
    }

}
