package com.wellit.project.notice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/notice")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @GetMapping
    public ResponseEntity<List<Question>> getAllQuestions() {
        List<Question> questions = questionService.findAll();
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Question> getQuestionById(@PathVariable Long id) {
        Question question = questionService.findById(id);
        if (question != null) {
            return ResponseEntity.ok(question);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/announcement")
    public String getAnnouncementPage(Model model) {
        List<Question> questions = questionService.findAll(); // 모든 질문 가져오기
        model.addAttribute("questions", questions);
        model.addAttribute("pageType", "announcement"); // pageType 추가
        return "notice/announcement"; // announcement.html로 이동
    }
    
    @GetMapping("/qna")
    public String getQnaPage(Model model) {
        // 질문 목록 가져오기
        List<Question> questions = questionService.findAll();
        model.addAttribute("questions", questions);
        model.addAttribute("pageType", "qna"); // pageType 추가
        return "notice/announcement"; // announcement.html을 반환
    }

    @GetMapping("/announcementQuestion")
    public String getAnnouncementQuestion() {
        return "notice/announcementQuestion";
    }
    
    @GetMapping("/qnaQuestion")
    public String getQnaQuestion() {
        return "notice/qnaQuestion";
    }

    @PostMapping(consumes = { "application/json" })
    public ResponseEntity<Question> createQuestion(@RequestBody Question question) {
        if (question.getQTitle() == null || question.getQTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Question savedQuestion = questionService.save(question);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQuestion);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        if (questionService.findById(id) != null) {
            questionService.delete(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private String saveImage(MultipartFile file) {
        String directory = "C:/Users/GREEN/git/WellIte/src/main/resources/static/imgs/notice"; // 실제 경로로 변경
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(directory, fileName);

        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return "/imgs/notice/" + fileName;
    }

    @PostMapping("/announcementQusetion")
    public void createQuestionWithImage(
            @RequestParam("qTitle") String qTitle,
            @RequestParam("qContent") String qContent,
            @RequestParam("qAuthor") String qAuthor,
            @RequestParam(value = "qImage", required = false) MultipartFile qImage,
            @RequestParam("qCategory") String qCategory,
            HttpServletResponse response) throws IOException {

        if (qTitle == null || qTitle.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Question question = new Question();
        question.setQTitle(qTitle);
        question.setQContent(qContent);
        question.setQAuthor(qAuthor);
        question.setQCategory(qCategory);
        question.setQCreatedTime(LocalDateTime.now());

        if (qImage != null && !qImage.isEmpty()) {
            String imageUrl = saveImage(qImage);
            if (imageUrl != null) {
                question.setQImageUrl(imageUrl);
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 이미지 저장 실패
                return;
            }
        }

        questionService.save(question);
        response.sendRedirect("/notice/announcement"); // 리다이렉트할 URL
    }
    @PostMapping("/qnaQuestion")
    public void createQnaQuestionWithImage(
            @RequestParam("qTitle") String qTitle,
            @RequestParam("qContent") String qContent,
            @RequestParam("qAuthor") String qAuthor,
            @RequestParam(value = "qImage", required = false) MultipartFile qImage,
            @RequestParam("qCategory") String qCategory,
            HttpServletResponse response) throws IOException {

        if (qTitle == null || qTitle.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Question question = new Question();
        question.setQTitle(qTitle);
        question.setQContent(qContent);
        question.setQAuthor(qAuthor);
        question.setQCategory(qCategory);
        question.setQCreatedTime(LocalDateTime.now());

        if (qImage != null && !qImage.isEmpty()) {
            String imageUrl = saveImage(qImage);
            if (imageUrl != null) {
                question.setQImageUrl(imageUrl);
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 이미지 저장 실패
                return;
            }
        }

        questionService.save(question);
        response.sendRedirect("/notice/qna"); // 리다이렉트할 URL
    }
    
    @GetMapping("/announcement/detail")
    public String getQuestionDetail(@RequestParam("id") Long id, Model model) {
        Question question = questionService.findById(id);
        model.addAttribute("question", question);
        return "/notice/announcementDetail"; // detail.html로 이동
    }
    
    @GetMapping("/qna/detail")
    public String getQnaDetail(@RequestParam("id") Long id, Model model) {
        Question question = questionService.findById(id);
        model.addAttribute("question", question);
        return "/notice/qnaDetail"; // detail.html로 이동
    }
    
    @GetMapping("/announcementEdit")
    public String getEditQuestionPage(@RequestParam("id") Long id, Model model) {
        Question question = questionService.findById(id);
        model.addAttribute("question", question);
        return "/notice/announcementEdit"; // 수정 페이지로 이동
    }
    
    @PostMapping("/announcement/update")
    public void updateQuestion(@RequestParam("questionId") Long id,
                               @ModelAttribute Question updatedQuestion,
                               @RequestParam("qImage") MultipartFile file,
                               HttpServletResponse response) throws IOException {
        Question existingQuestion = questionService.findById(id);
        if (existingQuestion == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 질문이 없으면 404 오류
            return;
        }

        existingQuestion.setQTitle(updatedQuestion.getQTitle());
        existingQuestion.setQContent(updatedQuestion.getQContent());
        existingQuestion.setQAuthor(updatedQuestion.getQAuthor());
        existingQuestion.setQCategory(updatedQuestion.getQCategory());

        // 이미지 파일 처리 로직 추가
        if (!file.isEmpty()) {
            // 파일 저장 로직 (예: 파일 시스템에 저장, DB에 저장 등)
            String imageUrl = saveImage(file); // saveFile 메서드 구현 필요
            existingQuestion.setQImageUrl(imageUrl); // 이미지 URL 설정
        }

        questionService.save(existingQuestion);
        response.sendRedirect("/notice/announcement");
    }
    
    @GetMapping("/announcementDelete")
    public void deleteQuestion(@RequestParam("questionId") Long id, HttpServletResponse response) throws IOException {
        if (!questionService.existsById(id)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 질문이 없으면 404 오류
            return;
        }

        questionService.delete(id); // 질문 삭제

        response.sendRedirect("/notice/announcement"); // 삭제 후 리다이렉트
    }
    @GetMapping("/qnaEdit")
    public String getEditQnaQuestionPage(@RequestParam("id") Long id, Model model) {
        Question question = questionService.findById(id);
        model.addAttribute("question", question);
        return "/notice/qnaEdit"; // 수정 페이지로 이동
    }
    
    @PostMapping("/qna/update")
    public void updateQnaQuestion(@RequestParam("questionId") Long id,
                               @ModelAttribute Question updatedQuestion,
                               @RequestParam("qImage") MultipartFile file,
                               HttpServletResponse response) throws IOException {
        Question existingQuestion = questionService.findById(id);
        if (existingQuestion == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 질문이 없으면 404 오류
            return;
        }

        existingQuestion.setQTitle(updatedQuestion.getQTitle());
        existingQuestion.setQContent(updatedQuestion.getQContent());
        existingQuestion.setQAuthor(updatedQuestion.getQAuthor());
        existingQuestion.setQCategory(updatedQuestion.getQCategory());

        // 이미지 파일 처리 로직 추가
        if (!file.isEmpty()) {
            // 파일 저장 로직 (예: 파일 시스템에 저장, DB에 저장 등)
            String imageUrl = saveImage(file); // saveFile 메서드 구현 필요
            existingQuestion.setQImageUrl(imageUrl); // 이미지 URL 설정
        }

        questionService.save(existingQuestion);
        response.sendRedirect("/notice/qna");
    }
    
    @GetMapping("/qnaDelete")
    public void deleteQnaQuestion(@RequestParam("questionId") Long id, HttpServletResponse response) throws IOException {
        if (!questionService.existsById(id)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 질문이 없으면 404 오류
            return;
        }

        questionService.delete(id); // 질문 삭제

        response.sendRedirect("/notice/qna"); // 삭제 후 리다이렉트
    }
}
