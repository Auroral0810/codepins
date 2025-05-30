package cn.ilikexff.codepins;

import cn.ilikexff.codepins.core.PinEntry;
import cn.ilikexff.codepins.core.PinStorage;

import cn.ilikexff.codepins.ui.SimpleTagEditorDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 动作：在当前行或选区添加一个图钉，并可附加备注。
 */
public class PinAction extends AnAction {

    public PinAction() {
        // 注意：图标在plugin.xml中设置，这里不需要设置
        // 使用空构造函数，避免覆盖plugin.xml中的设置
        System.out.println("[CodePins] PinAction registered"); // 插件加载时输出
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (editor == null || project == null) return;

        Caret caret = editor.getCaretModel().getPrimaryCaret();
        Document document = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) return;

        String note = Messages.showInputDialog(
                project,
                "请输入图钉备注（可选）：",
                "添加图钉",
                Messages.getQuestionIcon()
        );

        // 如果用户点击“取消”按钮，则中止添加图钉
        if (note == null) {
            return; // 用户取消了操作，直接返回
        }

        // 如果用户没有输入备注，则使用空字符串
        if (note.trim().isEmpty()) {
            note = "";
        }

        // 创建标签对话框，请求用户输入标签
        List<String> tags = new ArrayList<>();
        SimpleTagEditorDialog tagDialog = new SimpleTagEditorDialog(project, new PinEntry(
                file.getPath(),
                document.createRangeMarker(0, 0), // 临时标记，仅用于对话框
                note,
                System.currentTimeMillis(),
                System.getProperty("user.name"),
                false,
                tags
        ));

        if (tagDialog.showAndGet()) {
            // 如果用户点击了确定，获取标签
            tags = tagDialog.getTags();
        }

        boolean isBlock = caret.hasSelection();

        // 记录调试信息
        if (isBlock) {
            int startLine = document.getLineNumber(caret.getSelectionStart()) + 1;
            int endLine = document.getLineNumber(caret.getSelectionEnd()) + 1;
            System.out.println("[CodePins] 创建代码块图钉，行范围: " + startLine + "-" + endLine);
        } else {
            System.out.println("[CodePins] 创建单行图钉，行号: " + (document.getLineNumber(caret.getOffset()) + 1));
        }

        TextRange range = isBlock
                ? new TextRange(caret.getSelectionStart(), caret.getSelectionEnd())
                : new TextRange(caret.getOffset(), caret.getOffset());

        RangeMarker marker = document.createRangeMarker(range);
        marker.setGreedyToLeft(true);
        marker.setGreedyToRight(true);

        PinEntry pin = new PinEntry(
                file.getPath(),
                marker,
                note,
                System.currentTimeMillis(),
                System.getProperty("user.name"),
                isBlock,
                tags
        );

        boolean success = PinStorage.addPin(pin);

        // 状态栏和通知提示
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (success) {
            // 添加成功
            if (statusBar != null) {
                StatusBar.Info.set("✅ 图钉已添加", project);
            }
            Notifications.Bus.notify(new Notification(
                    "CodePins",
                    "图钉添加成功",
                    isBlock ? "已添加一段代码块图钉" : "已添加单行图钉",
                    NotificationType.INFORMATION
            ), project);
        } else {
            // 添加失败
            if (statusBar != null) {
                StatusBar.Info.set("❌ 图钉添加失败", project);
            }

            // 获取图钉数量信息
            Map<String, Integer> pinsInfo = PinStorage.getPinsCountInfo();
            int currentPins = pinsInfo.get("current");
            int maxPins = pinsInfo.get("max");

            // 获取标签数量信息
            Map<String, Integer> tagsInfo = PinStorage.getTagsCountInfo();
            int currentTagTypes = tagsInfo.get("current");
            int maxTagTypes = tagsInfo.get("max");
            int maxTagsPerPin = tagsInfo.get("perPin");

            // 确定失败原因
            String failureReason;
            String featureName;

            // 插件现在完全免费，这里不应该出现限制错误
            failureReason = "添加图钉失败，请稍后重试";

            // 创建通知
            Notification notification = new Notification(
                    "CodePins",
                    "图钉添加失败",
                    failureReason,
                    NotificationType.WARNING
            );

            // 插件现在完全免费，移除升级按钮

            Notifications.Bus.notify(notification, project);
        }
    }
}