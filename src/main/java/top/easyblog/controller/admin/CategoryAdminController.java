package top.easyblog.controller.admin;

import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import top.easyblog.autoconfig.QiNiuCloudService;
import top.easyblog.bean.Category;
import top.easyblog.bean.User;
import top.easyblog.commons.pagehelper.PageParam;
import top.easyblog.commons.pagehelper.PageSize;
import top.easyblog.commons.utils.FileUploadUtils;
import top.easyblog.config.web.Result;
import top.easyblog.service.impl.CategoryServiceImpl;

import javax.servlet.http.HttpSession;
import java.util.Objects;


/**
 * 用户后台文章分类管理
 */
@Controller
@RequestMapping(value = "/manage/category")
public class CategoryAdminController {


    private final String PREFIX = "/admin/type_manage/";
    private final String LOGIN_PAGE = "redirect:/user/loginPage";

    private final CategoryServiceImpl categoryService;
    private final QiNiuCloudService qiNiuCloudService;

    public CategoryAdminController(CategoryServiceImpl categoryService, QiNiuCloudService qiNiuCloudService) {
        this.categoryService = categoryService;
        this.qiNiuCloudService = qiNiuCloudService;
    }


    @GetMapping(value = "/list")
    public String categoryPage(HttpSession session,
                               Model model,
                               @RequestParam(value = "page", defaultValue = "1") int pageNo) {
        User user = (User) session.getAttribute("user");
        if (Objects.nonNull(user)) {
            PageParam pageParam = new PageParam(pageNo, PageSize.MIN_PAGE_SIZE.getPageSize());
            PageInfo<Category> categoriesPage = categoryService.getUserAllCategoriesPage(user.getUserId(), pageParam);
            model.addAttribute("categoriesPage", categoriesPage);
            model.addAttribute("userId", user.getUserId());
            putCategoryNumInModel(user, model);
            return PREFIX + "category-manage";
        }
        return LOGIN_PAGE;
    }

    @ResponseBody
    @RequestMapping(value = "/changeDisplay")
    public Result switchCategoryDisplay(@RequestParam(value = "categoryId") int categoryId,
                                        @RequestParam(value = "displayStatus") String displayStatus,
                                        HttpSession session) {
        final Result result = new Result();
        result.setSuccess(false);
        User user = (User) session.getAttribute("user");
        if (Objects.isNull(user)) {
            result.setMsg("请先登录后再操作");
            return result;
        }
        try {
            Category category = new Category();
            category.setCategoryId(categoryId);
            category.setDisplay(displayStatus);
            categoryService.updateByPKSelective(category);
            result.setSuccess(true);
            result.setMsg("Ok");
            return result;
        } catch (Exception e) {
            result.setMsg("服务异常，请重试！");
            return result;
        }
    }

    @GetMapping(value = "/delete")
    public String deleteCategory(@RequestParam(value = "categoryId") int categoryId) {
        if (categoryId > 0) {
            Category category = new Category();
            category.setCategoryId(categoryId);
            category.setDisplay("3");
            categoryService.updateByPKSelective(category);
            return "redirect:/manage/category/list";
        }
        return "/error/404";
    }


    @GetMapping(value = "/restore")
    public String restoreCategory(@RequestParam int categoryId) {
        if (categoryId > 0) {
            final Category category = new Category();
            category.setCategoryId(categoryId);
            category.setDisplay("1");
            categoryService.updateByPKSelective(category);
            return "redirect:/manage/category/dash";
        }
        return LOGIN_PAGE;
    }


    @GetMapping(value = "/deleteComplete")
    public String deleteComplete(@RequestParam int categoryId,
                                 @RequestParam String imageUrl) {
        if (categoryId > 0) {
            final Category category = new Category();
            category.setCategoryId(categoryId);
            if (!imageUrl.contains("static")) {
                try {
                    qiNiuCloudService.delete(imageUrl);
                } catch (Exception e) {
                    return "redirect:error/error";
                }
            }
            categoryService.deleteCategoryByCondition(category);
            return "redirect:/manage/category/dash";
        }
        return LOGIN_PAGE;
    }


    /**
     * 从分类管理首页删除的分类进入到垃圾箱中，垃圾箱页面
     *
     * @param session
     * @param model
     * @return
     */
    @GetMapping(value = "/dash")
    public String categoryDashBoxPage(HttpSession session,
                                      Model model,
                                      @RequestParam(value = "page", defaultValue = "1") int pageNo) {
        User user = (User) session.getAttribute("user");
        if (Objects.nonNull(user)) {
            PageParam pageParam = new PageParam(pageNo, PageSize.MIN_PAGE_SIZE.getPageSize());
            PageInfo<Category> categoriesPage = categoryService.getUserAllDeletedCategoryPage(user.getUserId(), pageParam);
            model.addAttribute("categoriesPage", categoriesPage);
            putCategoryNumInModel(user, model);
            return PREFIX + "category-dash";
        }
        return LOGIN_PAGE;
    }

    /**
     * 统计分类的数量
     *
     * @param displayStatus 分类的状态 0 不在前台显示  1 在前台显示  2 放在垃圾桶中（不会显示）
     * @return
     */
    private int getCategoryNum(User user, String displayStatus) {
        if (Objects.nonNull(user)) {
            Category category = new Category();
            category.setCategoryUser(user.getUserId());
            category.setDisplay(displayStatus);
            return categoryService.countSelective(category);
        }
        return 0;
    }

    private void putCategoryNumInModel(User user, Model model) {
        int var0 = getCategoryNum(user, "0");
        int var1 = getCategoryNum(user, "1");
        int var2 = getCategoryNum(user, "2");
        model.addAttribute("visibleCategoryNum", var0 + var1);
        model.addAttribute("deletedCategoryNum", var2);
    }

    @GetMapping(value = "/add")
    public String categoryAddPage(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (Objects.nonNull(user)) {
            return PREFIX + "category-add";
        }
        return LOGIN_PAGE;
    }

    @PostMapping(value = "/saveAdd")
    public String saveAdd(HttpSession session,
                          @RequestParam String categoryName,
                          @RequestParam(required = false, defaultValue = "") String categoryDesc,
                          @RequestParam(required = false) MultipartFile categoryImg,
                          RedirectAttributes attributes) {
        User user = (User) session.getAttribute("user");
        if (Objects.nonNull(user)) {
            Category var0 = categoryService.getCategoryByUserIdAndName(user.getUserId(), categoryName);
            if (Objects.nonNull(var0)) {
                attributes.addFlashAttribute("msg", "你已经存在该分类，请勿重复创建！");
                return "redirect:/manage/category/add";
            }
            Category category = new Category(user.getUserId(), categoryName, "", 0, 0, 0, "1", categoryDesc);
            if (categoryImg.getSize() == 0) {
                //用户新建分类的时用户没有上传图片，系统随机分配一张
                category.setCategoryImageUrl(FileUploadUtils.defaultCategoryImage());
            } else {
                //上传到七牛云图床，返回图片URL
                try {
                    category.setCategoryImageUrl(qiNiuCloudService.putMultipartImage(categoryImg));
                } catch (Exception e) {
                    return "/error/error";
                }
            }
            category.setCategoryName(categoryName);
            categoryService.saveCategory(category);
            return "redirect:/manage/category/list";
        }
        return LOGIN_PAGE;
    }


    @GetMapping(value = "/edit")
    public String categoryEditor(HttpSession session, @RequestParam int categoryId, Model model) {
        User user = (User) session.getAttribute("user");
        if (Objects.nonNull(user)) {
            final Category category = categoryService.getCategory(categoryId);
            model.addAttribute("category", category);
            return PREFIX + "category-edit";
        }
        return LOGIN_PAGE;
    }


    @PostMapping(value = "/saveEdit/{categoryId}")
    public String saveEditor(HttpSession session, @PathVariable("categoryId") int categoryId,
                             @RequestParam String categoryName,
                             @RequestParam String categoryDesc,
                             @RequestParam String oldCategoryImg,
                             @RequestParam MultipartFile categoryImage,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (Objects.nonNull(user)) {
            try {
                 Category category = new Category();
                category.setCategoryId(categoryId);
                category.setCategoryName(categoryName);
                category.setCategoryDescription(categoryDesc);
                if (categoryImage.getSize() > 0) {
                    try {
                        if (!oldCategoryImg.contains("static")) {
                            //删除在七牛云上的图片
                            qiNiuCloudService.delete(oldCategoryImg);
                        }
                        category.setCategoryImageUrl(qiNiuCloudService.putMultipartImage(categoryImage));
                    } catch (Exception e) {
                        return "/error/error";
                    }
                }
                int re = categoryService.updateByPKSelective(category);
                if (re > 0) {
                    return "redirect:/manage/category/list";
                } else {
                    return "redirect:/error/404";
                }
            } catch (Exception ex) {
                redirectAttributes.addFlashAttribute("err", "抱歉！，服务异常，请重试！");
                return "redirect:/manage/category/edit";
            }
        }
        return LOGIN_PAGE;
    }

}
