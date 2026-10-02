package com.crimsonred.reservation.member.presentation;

import static com.crimsonred.reservation.auth.presentation.CurrentMember.id;

import com.crimsonred.reservation.member.application.MemberService;
import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.shared.domain.InputText;
import com.crimsonred.reservation.shared.presentation.ApiPages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class MemberController {
  private final MemberService members;

  public MemberController(MemberService members) {
    this.members = members;
  }

  public record Review(@NotNull Boolean approve,
      @Size(max = Member.REASON_MAX_LENGTH, message = "사유는 최대 500자입니다.") String reason) {}

  public record Expel(@NotBlank @Size(max = Member.REASON_MAX_LENGTH, message = "사유는 최대 500자입니다.")
      @Pattern(regexp = InputText.MULTILINE_PATTERN, message = "사유에 보이지 않는 문자를 사용할 수 없습니다.") String reason) {}

  public record Role(@NotBlank String role) {}

  @GetMapping("/members/me")
  MemberService.View me(Authentication auth) {
    return members.view(members.current(id(auth)));
  }

  @PostMapping("/members/me/membership-request")
  MemberService.View request(Authentication auth) {
    return members.request(id(auth));
  }

  @GetMapping("/admin/members")
  ApiPages.Response<MemberService.View> list(
      Authentication auth,
      @RequestParam(required = false) String membership,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiPages.response(members.list(id(auth), membership, page, size));
  }

  @PostMapping("/admin/members/{memberId}/review")
  MemberService.View review(
      Authentication auth, @PathVariable Long memberId, @Valid @RequestBody Review input) {
    return members.review(id(auth), memberId, input.approve(), input.reason());
  }

  @PatchMapping("/admin/members/{memberId}/role")
  MemberService.View role(
      Authentication auth, @PathVariable Long memberId, @Valid @RequestBody Role input) {
    return members.changeRole(id(auth), memberId, input.role());
  }

  @PostMapping("/admin/members/{memberId}/expel")
  MemberService.View expel(
      Authentication auth, @PathVariable Long memberId, @Valid @RequestBody Expel input) {
    return members.expel(id(auth), memberId, input.reason());
  }
}
