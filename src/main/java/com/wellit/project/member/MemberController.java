package com.wellit.project.member;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.wellit.project.order.OrderService;
import com.wellit.project.order.PoHistoryForm;
import com.wellit.project.order.PurchaseOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wellit.project.email.EmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller
@RequiredArgsConstructor
@RequestMapping("/member")
public class MemberController {

	private final MemberService memberService;
	private final EmailService emailService;
	private final PasswordEncoder passwordEncoder;
	private final MemberRepository memberRepository;
	private final OrderService orderService;

	@GetMapping("/login")
	public String getLogin(@RequestParam(value = "error", required = false) String error, Model model) {
		if (error != null) {
			model.addAttribute("errorMessage", "아이디 또는 비밀번호가 일치하지 않습니다.");
		}
		return "/member/login";
	}

	@GetMapping("/register")
	public String register(MemberRegisterForm memberRegisterForm, @AuthenticationPrincipal UserDetails userDetails,
			Model model) {
		model.addAttribute("memberRegisterForm", new MemberRegisterForm());
		if (userDetails != null) {
			Member member = memberService.getMember(userDetails.getUsername());
			model.addAttribute("profileImage", member.getImageFile());
		}

		return "/member/register";
	}

	@PostMapping("/register")
	public String register(@Valid MemberRegisterForm memberRegisterForm, BindingResult bindingResult,
			@RequestParam("imageFile") MultipartFile imageFile, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "/member/register";
		}

		Boolean isIdVerified = (Boolean) session.getAttribute("idVerified");
		if (!isIdVerified || isIdVerified == null) {
			model.addAttribute("errorMessage", "아이디 중복확인이 완료되지 않았습니다. 회원가입을 진행할 수 없습니다.");
			return "/member/register";
		}

		Boolean isEmailVerified = (Boolean) session.getAttribute("emailVerified");
		if (!isEmailVerified || isEmailVerified == null) {
			model.addAttribute("errorMessage", "이메일 인증이 완료되지 않았습니다. 회원가입을 진행할 수 없습니다.");
			return "/member/register";
		}

		if (!memberRegisterForm.getMemberPassword().equals(memberRegisterForm.getMemberPassword2())) {
			bindingResult.rejectValue("memberPassword2", "passwordInCorrect", "2개의 패스워드가 서로 일치하지 않습니다.");
			return "/member/register";
		}

		try {
			memberService.create(memberRegisterForm.getMemberId(), memberRegisterForm.getMemberPassword(),
					memberRegisterForm.getMemberName(), memberRegisterForm.getMemberAlias(),
					memberRegisterForm.getMemberEmail(), memberRegisterForm.getMemberPhone(),
					memberRegisterForm.getMemberAddress(), memberRegisterForm.getMemberBirth(),
					memberRegisterForm.getMemberGender(), memberRegisterForm.getMemberVeganType(),
					memberRegisterForm.getZipcode(), memberRegisterForm.getRoadAddress(),
					memberRegisterForm.getAddressDetail(), memberRegisterForm.getBirth_year(),
					memberRegisterForm.getBirth_month(), memberRegisterForm.getBirth_day(), imageFile,
					memberRegisterForm.getIsBusiness(), memberRegisterForm.getBusinessName());

			session.removeAttribute("emailVerified");
			session.removeAttribute("verificationCode");
			session.removeAttribute("idVerified");

		} catch (DataIntegrityViolationException e) {
			e.printStackTrace();
			bindingResult.reject("registerFailed", "이미 등록된 사용자입니다.");
			return "/member/register";
		} catch (IOException e) {
			e.printStackTrace();
			bindingResult.reject("fileError", "파일 처리 중 오류가 발생했습니다.");
			return "/member/register";
		} catch (Exception e) {
			e.printStackTrace();
			bindingResult.reject("registerFailed", e.getMessage());
			return "/member/register";
		}
		model.addAttribute("loginMessage", "회원가입이 완료되었습니다. 로그인해주세요");
		return "/member/login";
	}

	@PostMapping("/check-id")
	public ResponseEntity<IdCheckResponse> checkId(@RequestBody IdCheckRequest request, HttpSession session) {
		boolean exists = memberService.isIdExists(request.getMemberId());
		IdCheckResponse response = new IdCheckResponse();
		response.setExists(exists);
		session.setAttribute("idVerified", true); // 인증 완료 상태 저장
		session.removeAttribute("verificationCode"); // 인증 코드 삭제
		return ResponseEntity.ok().body(response);
	}

	@PostMapping("/send-email")
	public ResponseEntity<String> sendEmail(@RequestParam("email") String email, HttpSession session) {

		// 이메일 중복 확인
		if (memberService.isEmailExists(email)) {
			return ResponseEntity.badRequest().body("이미 등록된 이메일입니다.");
		}

		String verificationCode = generateVerificationCode();

		session.setAttribute("verificationCode", verificationCode);
		String subject = "Wellit 인증코드";
		String text = "인증코드는 " + verificationCode + "입니다.";
		emailService.sendSimpleMessage(email, subject, text);

		return ResponseEntity.ok("인증 이메일이 전송되었습니다.");
	}

	@PostMapping("/verify-code")
	public ResponseEntity<String> verifyCode(@RequestParam("code") String code, HttpSession session) {
		String storedCode = (String) session.getAttribute("verificationCode");
		if (storedCode != null && storedCode.equals(code)) {
			session.setAttribute("emailVerified", true); // 인증 완료 상태 저장
			return ResponseEntity.ok("인증 성공");
		} else {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("인증 실패");
		}
	}

	private String generateVerificationCode() {
		Random random = new Random();
		int code = random.nextInt(900000) + 100000; // 6 digit code
		return String.valueOf(code);
	}

	@Getter
	@Setter
	public static class IdCheckRequest {
		private String memberId;
	}

	@Getter
	@Setter
	public static class IdCheckResponse {
		private boolean exists;
	}

	@GetMapping("/mypage")
	public String getMypage(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// 서비스 메소드 호출
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		// memberinfo 페이지로 리다이렉트
		return "redirect:/member/mypage/memberinfo";
	}

	@GetMapping("/enter_password")
	public String getEnterPassword(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// 서비스 메소드 호출
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {

			return "redirect:/member/login";
		}

		// 비밀번호 입력 페이지로 이동
		return "/member/enter_password";
	}

	@PostMapping("/enter_password")
	public String checkPassword(@RequestParam("enterpassword") String enterPassword, Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
			UserDetails userDetails = (UserDetails) authentication.getPrincipal();
			String memberId = userDetails.getUsername(); // 일반적으로 username이 memberId와 같음
			Member member = memberService.getMember(memberId);

			// 사용자가 입력한 비밀번호와 데이터베이스에 저장된 비밀번호를 비교
			if (passwordEncoder.matches(enterPassword, member.getMemberPassword())) {
				// 비밀번호가 일치하면 프로필 수정 페이지로 이동
				return "redirect:/member/update_profile";
			} else {
				// 비밀번호가 일치하지 않으면 에러 메시지를 모델에 추가
				model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
				model.addAttribute("member", member); // 다시 member 정보도 전달
				return "/member/enter_password";
			}
		}
		return "redirect:/member/login"; // 인증 정보가 없으면 로그인 페이지로 리다이렉트
	}

	@GetMapping("/update_profile")
	public String getUpdateProfile(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// 서비스 메소드 호출
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		return "/member/update_profile";
	}

	@PostMapping("/update_profile")
	public String updateProfile(@Valid MemberUpdateForm memberUpdateForm, BindingResult bindingResult,
			@RequestParam("imageFile") MultipartFile imageFile, HttpSession session, Model model,
			RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
		if (bindingResult.hasErrors()) {
			// 에러 메시지를 추출하여 줄바꿈으로 구분된 문자열로 변환
			String errorMessages = bindingResult.getAllErrors().stream().map(error -> {
				// 필드 오류 메시지
				if (error instanceof FieldError) {
					FieldError fieldError = (FieldError) error;
					return fieldError.getDefaultMessage();
				}
				// 글로벌 오류 메시지
				return error.getDefaultMessage();
			}).collect(Collectors.joining("\n")); // 줄바꿈으로 메시지 구분

			// 에러 메시지를 모델에 추가
			model.addAttribute("errorMessage", errorMessages);
			model.addAttribute("member", memberUpdateForm); // 현재 폼 객체를 모델에 추가

			// 프로필 수정 폼 페이지로 이동
			return "member/update_profile";
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof UserDetails) {

			UserDetails userDetails = (UserDetails) authentication.getPrincipal();
			String memberId = userDetails.getUsername();

			Member existingMember = memberService.getMember(memberId);
			model.addAttribute("member", memberUpdateForm); // 여기에 추가
			// 비밀번호 일치 여부 확인

			// 비밀번호 검사: 비밀번호가 입력된 경우에만 유효성 검사 진행
			String newPassword = existingMember.getMemberPassword(); // 기존 비밀번호 유지
			if (memberUpdateForm.getMemberPassword() != null && !memberUpdateForm.getMemberPassword().isEmpty()) {
				// 비밀번호 유효성 검사 (6자 이상, 영문과 숫자 포함)
				if (!memberUpdateForm.getMemberPassword().matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$")) {
					bindingResult.rejectValue("memberPassword", "error.memberPassword",
							"비밀번호는 최소 6자 이상이어야 하며, 영문과 숫자를 포함해야 합니다.");
					model.addAttribute("errorMessage", "비밀번호는 최소 6자 이상이어야 하며, 영문과 숫자를 포함해야 합니다.");
					return "member/update_profile";
				}

				// 비밀번호 확인 검사
				if (!memberUpdateForm.getMemberPassword().equals(memberUpdateForm.getMemberPassword2())) {
					bindingResult.rejectValue("memberPassword2", "passwordInCorrect", "2개의 패스워드가 서로 일치하지 않습니다.");
					model.addAttribute("errorMessage", "2개의 패스워드가 서로 일치하지 않습니다.");
					return "member/update_profile";
				}

				newPassword = memberUpdateForm.getMemberPassword(); // 새 비밀번호로 갱신
			}
			// 이메일 변경 확인 및 이메일 인증 처리
			if (!existingMember.getMemberEmail().equals(memberUpdateForm.getMemberEmail())) {
				Boolean isEmailVerified = (Boolean) session.getAttribute("emailVerified");
				if (Boolean.FALSE.equals(isEmailVerified) || isEmailVerified == null) {
					model.addAttribute("errorMessage", "이메일 인증이 완료되지 않았습니다.");
					model.addAttribute("member", memberUpdateForm); // 여기에 추가
					return "/member/update_profile";
				}
			}

			// 년도 값이 null인 경우 기존 값을 유지
			if (memberUpdateForm.getBirth_year() == null || memberUpdateForm.getBirth_year().isEmpty()) {
				memberUpdateForm.setBirth_year(memberUpdateForm.getBirth_year());
			}

			// 월 값이 null인 경우 기존 값을 유지
			if (memberUpdateForm.getBirth_month() == null || memberUpdateForm.getBirth_month().isEmpty()) {
				memberUpdateForm.setBirth_month(memberUpdateForm.getBirth_month());
			}

			// 일 값이 null인 경우 기존 값을 유지
			if (memberUpdateForm.getBirth_day() == null || memberUpdateForm.getBirth_day().isEmpty()) {
				memberUpdateForm.setBirth_day(memberUpdateForm.getBirth_day());
			}

			try {
				// 기존 이미지 경로를 폼에서 가져와서 전달
				String existingImagePath = existingMember.getImageFile();

				memberService.updateMember(existingMember, memberUpdateForm.getMemberPassword(),
						memberUpdateForm.getMemberName(), memberUpdateForm.getMemberAlias(),
						memberUpdateForm.getMemberEmail(), memberUpdateForm.getMemberPhone(),
						memberUpdateForm.getMemberAddress(), memberUpdateForm.getMemberBirth(),
						memberUpdateForm.getMemberGender(), memberUpdateForm.getMemberVeganType(),
						memberUpdateForm.getZipcode(), memberUpdateForm.getRoadAddress(),
						memberUpdateForm.getAddressDetail(), memberUpdateForm.getBirth_year(),
						memberUpdateForm.getBirth_month(), memberUpdateForm.getBirth_day(), imageFile,
						existingImagePath);

				session.removeAttribute("emailVerified");
				session.removeAttribute("verificationCode");

			} catch (DataIntegrityViolationException e) {
				e.printStackTrace();
				bindingResult.reject("updateFailed", "이미 등록된 사용자 정보입니다.");
				model.addAttribute("member", memberUpdateForm); // 여기에 추가
				return "/member/update_profile";
			} catch (IOException e) {
				e.printStackTrace();
				bindingResult.reject("fileError", "파일 처리 중 오류가 발생했습니다.");
				model.addAttribute("member", memberUpdateForm); // 여기에 추가
				return "/member/update_profile";
			} catch (Exception e) {
				e.printStackTrace();
				bindingResult.reject("updateFailed", e.getMessage());
				model.addAttribute("member", memberUpdateForm); // 여기에 추가
				return "/member/update_profile";
			}
		} else {
			return "redirect:/member/login";
		}
		session.invalidate();
		redirectAttributes.addFlashAttribute("updateMessage", "회원 수정이 완료되었습니다. 다시 로그인해주세요.");
		return "redirect:/member/login";
	}

	@GetMapping("/delete_password")
	public String getDeleteMember(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		return "/member/delete_password";
	}

	@PostMapping("/delete_password")
	public String deleteMember(@RequestParam("deletepassword") String password, HttpSession session,
			RedirectAttributes redirectAttributes, Model model) {
		// 인증된 사용자 가져오기
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
			UserDetails userDetails = (UserDetails) authentication.getPrincipal();
			String memberId = userDetails.getUsername();

			// 비밀번호 확인
			Member member = memberService.getMember(memberId);
			if (passwordEncoder.matches(password, member.getMemberPassword())) {
				// 비밀번호가 일치하면 삭제 처리
				redirectAttributes.addFlashAttribute("successMessage", "회원 탈퇴가 완료되었습니다.");
				memberService.deleteMember(memberId);
				session.invalidate(); // 세션 무효화
				return "redirect:/";
			} else {
				// 비밀번호가 일치하지 않으면 에러 메시지를 모델에 추가
				model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
				model.addAttribute("member", member); // 다시 member 정보도 전달
				return "/member/delete_password";
			}
		}
		return "/";
	}

	@GetMapping("/findMemberId")
	public String getFindMemberId() {
		return "/member/findMemberId";
	}

	@GetMapping("/findId")
	public String getFindId() {
		return "/member/findId";
	}

	@PostMapping("/findId")
	public ModelAndView findId(@RequestParam("memberName") String memberName,
			@RequestParam("memberEmail") String memberEmail, HttpSession session) {
		Optional<Member> member = memberService.findByNameAndEmail(memberName, memberEmail);
		Boolean isEmailVerified = (Boolean) session.getAttribute("emailVerified");

		if (!isEmailVerified || isEmailVerified == null) {
			ModelAndView modelAndView = new ModelAndView("/member/findMemberId");
			modelAndView.addObject("errorMessage", "이메일 인증이 완료되지 않았습니다.");
			return modelAndView;
		}

		ModelAndView modelAndView = new ModelAndView("member/findId");
		if (member.isPresent()) {
			Member thisMember = member.get();
			modelAndView.addObject("message", "회원님의 아이디는 " + thisMember.getMemberId() + "입니다.");
		} else {
			modelAndView.addObject("errorMessage", "입력하신 정보와 일치하는 회원이 없습니다.");
		}
		session.setAttribute("emailVerified", false);
		session.removeAttribute("verificationCode");
		return modelAndView;
	}

	@PostMapping("/id-email")
	public ResponseEntity<?> sendIdEmail(@RequestParam("memberEmail") String email, HttpSession session) {

		Optional<Member> member = memberService.findByMemberEmail(email);

		if (!member.isPresent()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("입력하신 이메일이 존재하지 않습니다.");
		}

		String verificationCode = generateVerificationCode();
		session.setAttribute("verificationCode", verificationCode);
		String subject = "Wellit 아이디 찾기 인증코드";
		String text = "인증코드는 " + verificationCode + "입니다.";
		emailService.sendSimpleMessage(email, subject, text);

		return ResponseEntity.ok("인증 이메일이 전송되었습니다.");
	}

	@PostMapping("/pw-email")
	public ResponseEntity<String> sendPwEmail(@RequestParam("memberEmail") String email, HttpSession session) {

		Optional<Member> member = memberService.findByMemberEmail(email);

		if (!member.isPresent()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("입력하신 이메일이 존재하지 않습니다.");
		}

		String verificationCode = generateVerificationCode();
		session.setAttribute("verificationCode", verificationCode);
		String subject = "Wellit 비밀번호 찾기 인증코드";
		String text = "인증코드는 " + verificationCode + "입니다.";
		emailService.sendSimpleMessage(email, subject, text);

		return ResponseEntity.ok("인증 이메일이 전송되었습니다.");
	}

	@GetMapping("/findPassword")
	public String getFindPassword() {
		return "/member/findPassword";
	}

	@PostMapping("/findPassword")
	public ResponseEntity<String> findPassword(@RequestParam("memberEmail") String email, HttpSession session,
			Model model) {

		Optional<Member> thisMember = memberService.findByMemberEmail(email);

		if (!thisMember.isPresent()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("입력하신 이메일이 존재하지 않습니다.");
		}

		// 이메일 인증 여부 확인
		Boolean isEmailVerified = (Boolean) session.getAttribute("emailVerified");
		System.out.println("isEmailVerified: " + isEmailVerified);
		// 이메일 인증이 완료되지 않았을 때
		if (isEmailVerified == null || !isEmailVerified) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("이메일 인증이 완료되지 않았습니다.");
		}

		return memberService.sendPasswordResetEmail(email);
	}

	@GetMapping("/reset_password")
	public String getResetPassword(@RequestParam("token") String token, Model model) {
		model.addAttribute("token", token);
		System.out.println("Token received: " + token); // 토큰 값을 로그에 출력
		return "/member/reset_password";
	}

	@PostMapping("/reset_password")
	@Transactional
	public ResponseEntity<String> resetPassword(@RequestParam("token") String token,
			@RequestParam("newPassword") String password, @RequestParam("newPassword2") String password2, Model model) {

		// 비밀번호 형식 검증
		String passwordPattern = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$";
		if (!Pattern.matches(passwordPattern, password)) {
			return ResponseEntity.badRequest().body("비밀번호는 최소 6자 이상이어야 하며, 문자와 숫자를 포함해야 합니다.");
		}

		// 비밀번호 확인 일치 검증
		if (!password.equals(password2)) {
			return ResponseEntity.badRequest().body("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
		}

		// 토큰 검증
		model.addAttribute("token", token); // 모델에 token 추가
		System.out.println("Token received: " + token);
		Optional<Member> thisMember = memberRepository.findByResetToken(token);
		if (thisMember.isEmpty()) {
			return ResponseEntity.badRequest().body("유효하지 않은 토큰입니다.");
		}

		Member member = thisMember.get();

		// 비밀번호 업데이트
		member.setMemberPassword(passwordEncoder.encode(password));
		member.setResetToken(null); // 토큰 무효화
		memberRepository.save(member);

		return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
	}

	public String getMethodName(@RequestParam String param) {
		return new String();
	}

//마이페이지 프래그먼트들

	@GetMapping("/mypage/favorite/product")
	public String getFavoriteShop(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}
		return "/shop/mypage_favoriteProduct";
	}

	@GetMapping("/mypage/favorite/store")
	public String getFavoriteStore(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}
		return "/load/mypage_favoriteStore";
	}

	@GetMapping("/mypage/orderhistory")
	public String getOrderHistory(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// 인증된 사용자의 정보를 모델에 추가
		memberService.getPrincipal(authentication, model);

		// 모델에서 member와 formattedRegDate를 가져옵니다.
		Member member = (Member) model.getAttribute("member");
		String formattedRegDate = (String) model.getAttribute("formattedRegDate");
		// mypage : 주문 내역 확인
		List<PoHistoryForm> poHistoryList = orderService.getPoHistoryList(member.getMemberId());
		model.addAttribute("poHistoryList", poHistoryList);
		model.addAttribute("member", member);
		model.addAttribute("formattedRegDate", formattedRegDate);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		return "/order/mypage_orderHistory";

	}

	@GetMapping("/mypage/memberinfo")
	public String getMemberInfo(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		return "/member/memberinfo";
	}

	@GetMapping("/mypage/myreservations")
	public String getMyReservation(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		return "/load/mypage_myreservations";
	}

	@GetMapping("/mypage/myStoreReservations")
	public String getMyStoreReservations(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}

		return "/load/mypage_myStoreReservations";
	}

	@GetMapping("/load/admin/store/create")
	public String getStoreForm(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}
		return "/manager/mypage_storeForm";
	}
	
	@GetMapping("/admin/memberList")
	public String getMemberList(Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		memberService.getPrincipal(authentication, model);

		// 인증 정보가 없는 경우 로그인 페이지로 리다이렉트
		if (!model.containsAttribute("member")) {
			return "redirect:/member/login";
		}
		
		List<Member> members = memberService.findAllMembers();
        model.addAttribute("members", members);

        // 회원가입 일자 기준으로 정렬
        members.sort(Comparator.comparing(Member::getMemberRegDate));
		
		return "/manager/memberList";
	}
	
	@DeleteMapping("/admin/memberDelete/{memberId}")
	public ResponseEntity<Void> deleteMember(@PathVariable("memberId") String memberId) {
		try {
	        if (memberService.getMember(memberId) != null) {
	            memberService.deleteMember(memberId);
	            return ResponseEntity.noContent().build(); // 성공적으로 삭제됨
	        } else {
	            return ResponseEntity.notFound().build(); // 회원을 찾을 수 없음
	        }
	    } catch (Exception e) {
	        // 삭제 중 발생한 오류를 로그로 남김
	        System.err.println("삭제 중 오류 발생: " + e.getMessage());
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 내부 서버 오류 응답
	    }
	}
	
	// GET: 관리자의 회원 수정 페이지 로드
	@GetMapping("/admin/member/{memberId}/update_profile")
	public String getAdminUpdateProfile(@PathVariable("memberId") String memberId, Model model) {
		 
		// 회원 ID로 회원 정보 조회
	    Member member = memberService.getMember(memberId);
	    
	    // 회원이 존재하지 않는 경우 예외 처리
	    if (member == null) {
	        throw new IllegalArgumentException("해당 회원이 존재하지 않습니다. ID: " + memberId);
	    }
	    
	    // 회원의 memberType이 'KAKAO'라면 update_kakao 페이지로 이동
	    if ("KAKAO".equals(member.getMemberType())) {
	        return "member/update_kakao";
	    }
	    
	    // 그 외의 경우 일반 회원 수정 페이지로 이동
	    return "member/update_profile";
	}

	// POST: 관리자의 회원 정보 수정 처리
	@PostMapping("/admin/member/{id}/update_profile")
	public String adminUpdateProfile(
	    @PathVariable("id") String memberId, 
	    @Valid MemberUpdateForm memberUpdateForm, 
	    BindingResult bindingResult, 
	    @RequestParam("imageFile") MultipartFile imageFile, 
	    HttpSession session, 
	    Model model, 
	    RedirectAttributes redirectAttributes) {
	    
	    if (bindingResult.hasErrors()) {
	        // 유효성 검사 실패 시 에러 메시지 처리
	        model.addAttribute("errorMessage", "유효성 검사에 실패했습니다.");
	        return "/admin/member/update_profile"; // 다시 수정 페이지로 돌아감
	    }

	    Member existingMember = memberService.getMember(memberId);
	    if (existingMember == null) {
	        return "redirect:/admin/member/list"; // 회원이 없으면 목록 페이지로 리다이렉트
	    }

	    // 관리자 수정 로직 (기존과 유사)
	    try {
	        // 기존 이미지 경로를 폼에서 가져와서 전달
	        String existingImagePath = existingMember.getImageFile();

	        // 회원 정보 업데이트 (필요한 서비스 메소드 호출)
	        memberService.updateMember(
	            existingMember, 
	            memberUpdateForm.getMemberPassword(),
	            memberUpdateForm.getMemberName(),
	            memberUpdateForm.getMemberAlias(),
	            memberUpdateForm.getMemberEmail(),
	            memberUpdateForm.getMemberPhone(),
	            memberUpdateForm.getMemberAddress(),
	            memberUpdateForm.getMemberBirth(),
	            memberUpdateForm.getMemberGender(),
	            memberUpdateForm.getMemberVeganType(),
	            memberUpdateForm.getZipcode(),
	            memberUpdateForm.getRoadAddress(),
	            memberUpdateForm.getAddressDetail(),
	            memberUpdateForm.getBirth_year(),
	            memberUpdateForm.getBirth_month(),
	            memberUpdateForm.getBirth_day(),
	            imageFile,
	            existingImagePath
	        );

	        redirectAttributes.addFlashAttribute("updateMessage", "회원 정보가 성공적으로 수정되었습니다.");
	    } catch (Exception e) {
	        // 예외 처리
	        e.printStackTrace();
	        model.addAttribute("errorMessage", "회원 정보 수정 중 오류가 발생했습니다.");
	        return "/admin/member/update_profile";
	    }

	    return "redirect:/admin/member/list"; // 수정 후 회원 목록 페이지로 리다이렉트
	}
}