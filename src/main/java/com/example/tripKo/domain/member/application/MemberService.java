package com.example.tripKo.domain.member.application;

import com.example.tripKo._core.errors.exception.Exception400;
import com.example.tripKo._core.errors.exception.Exception404;
import com.example.tripKo._core.errors.exception.Exception500;
import com.example.tripKo._core.security.JwtProvider;
import com.example.tripKo._core.security.data.JwtToken;
import com.example.tripKo.domain.file.dao.FileRepository;
import com.example.tripKo.domain.member.MemberReservationStatus;
import com.example.tripKo.domain.member.MemberRoleType;
import com.example.tripKo.domain.member.application.convenience.CheckDuplicateService;
import com.example.tripKo.domain.member.dao.MemberRepository;
import com.example.tripKo.domain.member.dao.MemberReservationInfoRepository;
import com.example.tripKo.domain.member.dto.request.SignInRequest;
import com.example.tripKo.domain.member.dto.response.FestivalReservationResponse;
import com.example.tripKo.domain.member.dto.request.userInfo.UserInfoRequest;
import com.example.tripKo.domain.member.dto.response.RestaurantReservationResponse;
import com.example.tripKo.domain.member.dto.response.review.ReviewsListResponse;
import com.example.tripKo.domain.member.dto.response.review.ReviewsResponse;
import com.example.tripKo.domain.member.dto.response.userInfo.UserInfoResponse;
import com.example.tripKo.domain.member.entity.Member;
import com.example.tripKo.domain.member.entity.MemberReservationInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.example.tripKo.domain.place.dao.PlaceFestivalRepository;
import com.example.tripKo.domain.place.PlaceType;
import com.example.tripKo.domain.place.dao.PlaceRepository;
import com.example.tripKo.domain.place.dao.PlaceRestaurantRepository;
import com.example.tripKo.domain.place.dto.request.FestivalReservationConfirmRequest;
import com.example.tripKo.domain.place.dao.ReviewRepository;
import com.example.tripKo.domain.place.dto.request.RestaurantReservationConfirmRequest;
import com.example.tripKo.domain.place.dto.response.info.FestivalReservationConfirmResponse;
import com.example.tripKo.domain.place.dto.response.info.FestivalReservationSelectResponse;
import com.example.tripKo.domain.place.dto.request.ReviewUpdateRequest;
import com.example.tripKo.domain.place.dto.response.info.RestaurantReservationConfirmResponse;
import com.example.tripKo.domain.place.dto.response.info.RestaurantReservationSelectResponse;
import com.example.tripKo.domain.place.entity.Place;
import com.example.tripKo.domain.place.entity.PlaceFestival;
import com.example.tripKo.domain.place.entity.PlaceRestaurant;
import com.example.tripKo.domain.place.entity.Review;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

  private final MemberReservationInfoRepository memberReservationInfoRepository;
  private final MemberRepository memberRepository;
  private final PlaceRestaurantRepository placeRestaurantRepository;
  private final PlaceFestivalRepository placeFestivalRepository;
  private final PlaceRepository placeRepository;
  private final JwtProvider jwtProvider;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManagerBuilder authenticationManagerBuilder;
  private final CheckDuplicateService checkDuplicateService;
  private final FileRepository fileRepository;
  private final ReviewRepository reviewRepository;

  @Transactional
  public UserInfoResponse getUserInfo(Member member) {
    UserInfoResponse userInfoResponse = UserInfoResponse.builder().member(member).build();
    return userInfoResponse;
  }

  @Transactional
  public void setUserInfo(Member member, UserInfoRequest userInfoRequest) {
    //이메일 중복 여부 체크
    Member emailCheck = memberRepository.findByEmailAddressAndMemberIdNot(userInfoRequest.getEmail(),
        member.getMemberId()).orElse(null);
    if (emailCheck != null) {
      throw new Exception404("이미 다른 사람이 사용 중인 이메일입니다. email : " + userInfoRequest.getEmail());
    }
    member.updateUserInfo(userInfoRequest);
    memberRepository.save(member);
  }

  @Transactional
  public void setUserInfoImage(Member member, MultipartFile image) {
    com.example.tripKo.domain.file.entity.File file = saveImages(image);
    file = fileRepository.save(file);

    member.updateFile(file);
    memberRepository.save(member);
  }

  @Transactional
  public List<ReviewsResponse> getRestaurantReviews(Member member, int page) {
    Pageable pageable = PageRequest.of(page, 10);
    List<Review> reviews = reviewRepository.findAllByMemberAndPlaceType(member, PlaceType.RESTAURANT, pageable);
    List<ReviewsResponse> reviewsResponses = reviews.stream().map(r -> ReviewsResponse.builder().review(r).build())
        .collect(Collectors.toList());
    return reviewsResponses;
  }

  @Transactional
  public List<ReviewsResponse> getFestivalReviews(Member member, int page) {
    Pageable pageable = PageRequest.of(page, 10);
    List<Review> reviews = reviewRepository.findAllByMemberAndPlaceType(member, PlaceType.FESTIVAL, pageable);
    List<ReviewsResponse> reviewsResponses = reviews.stream().map(r -> ReviewsResponse.builder().review(r).build())
        .collect(Collectors.toList());
    return reviewsResponses;
  }

  @Transactional
  public List<ReviewsResponse> getTouristSpotReviews(Member member, int page) {
    Pageable pageable = PageRequest.of(page, 10);
    List<Review> reviews = reviewRepository.findAllByMemberAndPlaceType(member, PlaceType.TOURIST_SPOT, pageable);
    List<ReviewsResponse> reviewsResponses = reviews.stream().map(r -> ReviewsResponse.builder().review(r).build())
        .collect(Collectors.toList());
    return reviewsResponses;
  }

  @Transactional
  public ReviewsResponse getReviewDetail(Member member, Long id) {
    Review review = reviewRepository.findById(id)
        .orElseThrow(() -> new Exception404("해당하는 리뷰를 찾을 수 없습니다. id : " + id));
    if (!review.getMember().equals(member)) {
      new Exception404("본인의 리뷰가 아닙니다. id : " + id);
    }

    ReviewsResponse reviewsResponse = ReviewsResponse.builder().review(review).build();
    return reviewsResponse;
  }

  @Transactional
  public ReviewsListResponse getAllReviews(Member member, int page) {
    Pageable pageable = PageRequest.of(page, 10);
    List<Review> reviews = reviewRepository.findAllByMember(member, pageable);
    List<Review> restaurant = new ArrayList<>();
    List<Review> festival = new ArrayList<>();
    List<Review> touristSpot = new ArrayList<>();
    for (Review r : reviews) {
      switch (r.getType()) {
        case RESTAURANT:
          restaurant.add(r);
          break;
        case FESTIVAL:
          festival.add(r);
          break;
        case TOURIST_SPOT:
          touristSpot.add(r);
          break;
      }
    }
    ReviewsListResponse reviewsListResponse = ReviewsListResponse.builder().restaurant(restaurant).festival(festival)
        .touristSpot(touristSpot).build();
    return reviewsListResponse;
  }

  @Transactional
  public List<RestaurantReservationResponse> getRestaurantReservationInfo(Member member) {
    List<MemberReservationInfo> memberReservationInfoList = memberReservationInfoRepository.findAllByMember(member);
    List<RestaurantReservationResponse> responseList = memberReservationInfoList.stream()
        .map(memberReservationInfo -> RestaurantReservationResponse.from(memberReservationInfo))
        .collect(Collectors.toList());
    return responseList;
  }

  @Transactional
  public RestaurantReservationSelectResponse selectRestaurantReservationDate(Long id) {
    PlaceRestaurant placeRestaurant = placeRestaurantRepository.findById(id)
        .orElseThrow(() -> new Exception404("해당하는 식당을 찾을 수 없습니다. id : " + id));
    RestaurantReservationSelectResponse ResponseDTO = new RestaurantReservationSelectResponse(placeRestaurant);
    return ResponseDTO;
  }

  @Transactional
  public RestaurantReservationConfirmResponse confirmRestaurantReservation(
      Member member,
      RestaurantReservationConfirmRequest requestDTO) {
//    Member memberInfo = memberRepository.findById(requestDTO.getReservation().getMemberId())
//        .orElseThrow(() -> new Exception404("유저를 찾을 수 없습니다. id : " + requestDTO.getReservation().getMemberId()));
    Place place = placeRepository.findById(requestDTO.getReservation().getPlaceId())
        .orElseThrow(() -> new Exception404("해당하는 식당을 찾을 수 없습니다. id : " + requestDTO.getReservation().getPlaceId()));
    MemberReservationInfo saveMemberReservationInfo = new MemberReservationInfo(
        member,
        requestDTO.getReservation().getHeadCount(),
        MemberReservationStatus.예약완료,
        place,
        requestDTO.getReservation().getReservationDate(),
        requestDTO.getReservation().getReservationTime(),
        requestDTO.getReservation().getMessage()
    );
    memberReservationInfoRepository.save(saveMemberReservationInfo);

    MemberReservationInfo memberReservationInfo = memberReservationInfoRepository.findById(
            requestDTO.getReservation().getId())
        .orElseThrow(() -> new Exception404("예약이 완료되지 않았습니다. id : " + requestDTO.getReservation().getId()));
    RestaurantReservationConfirmResponse ResponseDTO = new RestaurantReservationConfirmResponse(memberReservationInfo);
    return ResponseDTO;
  }

  @Transactional
  public List<FestivalReservationResponse> getFestivalReservationInfo(Member member) {
    List<MemberReservationInfo> memberReservationInfoList = memberReservationInfoRepository.findAllByMember(member);
    List<FestivalReservationResponse> responseList = memberReservationInfoList.stream()
        .map(memberReservationInfo -> FestivalReservationResponse.from(memberReservationInfo))
        .collect(Collectors.toList());
    return responseList;
  }

  @Transactional
  public FestivalReservationSelectResponse selectFestivalReservationDate(Long id) {
    PlaceFestival placeFestival = placeFestivalRepository.findById(id)
        .orElseThrow(() -> new Exception404("해당하는 축제를 찾을 수 없습니다. id : " + id));
    FestivalReservationSelectResponse ResponseDTO = new FestivalReservationSelectResponse(placeFestival);
    return ResponseDTO;
  }

  @Transactional
  public FestivalReservationConfirmResponse confirmFestivalReservation(
      Member member,
      FestivalReservationConfirmRequest requestDTO) {
//    Member memberInfo = memberRepository.findById(requestDTO.getReservation().getMemberId())
//        .orElseThrow(() -> new Exception404("유저를 찾을 수 없습니다. id : " + requestDTO.getReservation().getMemberId()));
    Place place = placeRepository.findById(requestDTO.getReservation().getPlaceId())
        .orElseThrow(() -> new Exception404("해당하는 축제를 찾을 수 없습니다. id : " + requestDTO.getReservation().getPlaceId()));
    MemberReservationInfo saveMemberReservationInfo = new MemberReservationInfo(
        member,
        requestDTO.getReservation().getHeadCount(),
        MemberReservationStatus.예약완료,
        place,
        requestDTO.getReservation().getReservationDate(),
        "", // 축제 예약은 시간 선택 기능이 없으니 빈 string으로 넘겨줌
        requestDTO.getReservation().getMessage()
    );
    memberReservationInfoRepository.save(saveMemberReservationInfo);

    MemberReservationInfo memberReservationInfo = memberReservationInfoRepository.findById(
            requestDTO.getReservation().getId())
        .orElseThrow(() -> new Exception404("예약이 완료되지 않았습니다. id : " + requestDTO.getReservation().getId()));
    FestivalReservationConfirmResponse ResponseDTO = new FestivalReservationConfirmResponse(memberReservationInfo);
    return ResponseDTO;
  }


  @Transactional
  public void signUp(String memberId, String password, String nickName, String realName, String email,
      String nationality) {
    checkIsDuplicateEmail(email);
    checkIsDuplicateLoginId(memberId);

    Member member = Member.builder()
        .memberId(memberId)
        .password(passwordEncoder.encode(password))
        .nickName(nickName)
        .realName(realName)
        .emailAddress(email)
        .nationality(nationality)
        .role(MemberRoleType.MEMBER)
        .birthday("700101")
        .build();

    memberRepository.save(member);
  }

  public JwtToken signIn(SignInRequest request) {
    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
        request.getMemberId(), request.getPassword());
    Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
    return jwtProvider.generateToken(authentication);
  }

  private void checkIsDuplicateEmail(String email) {
    if (checkDuplicateService.isDuplicateEmail(email)) {
      throw new Exception404("동일한 이메일이 존재합니다.");
    }
  }

  private void checkIsDuplicateLoginId(String memberId) {
    if (checkDuplicateService.isDuplicateLoginId(memberId)) {
      throw new Exception404("동일한 아이디가 존재합니다.");
    }
  }

  private com.example.tripKo.domain.file.entity.File saveImages(MultipartFile image) {
    //이미지가 저장될 경로는 /src/main/resources/reviews/images/
    String imagesPath = new File("").getAbsolutePath() + File.separator
        + "src" + File.separator
        + "main" + File.separator
        + "resources" + File.separator
        + "reviews" + File.separator
        + "images";

    File imageFile = new File(imagesPath);

    //resources/review/images에 디렉토리 생성
    if (!imageFile.exists()) {
      boolean isExists = imageFile.mkdirs();
      if (!isExists) {
        throw new Exception500("경로 생성에 실패하였습니다.");
      }
    }

    String contentType = image.getContentType();
    String imageName = image.getOriginalFilename();
    String fileExtension;

    //jpeg, jpg, png만 허용
    if (Objects.isNull(contentType)) {
      throw new Exception400("올바르지 않은 파일 확장자 형식입니다.");
    } else if (contentType.contains("image/jpeg") ||
        contentType.contains("image/jpg")) {
      fileExtension = ".jpg";
    } else if (contentType.contains("image/png")) {
      fileExtension = ".png";
    } else {
      throw new Exception400("올바르지 않은 파일 확장자 형식입니다.");
    }

    String newFileName = String.format("%1$016x", System.nanoTime()) + "_" + imageName;
    System.out.println("====================");
    System.out.println(newFileName);

    com.example.tripKo.domain.file.entity.File fileEntity = com.example.tripKo.domain.file.entity.File.builder()
        .type(contentType)
        .name(newFileName)
        .build();

    String imagePath = imagesPath + File.separator + newFileName;
    File savedImage = new File(imagePath);
    try {
      image.transferTo(savedImage);
    } catch (IOException e) {
      throw new Exception500("이미지를 저장하는 중 문제가 발생하였습니다.");
    }

    return fileEntity;
  }

}
